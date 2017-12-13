/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.things.lowpan.transmitter;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.contrib.driver.lowpan.UartLowpanDriver;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.lowpan.LowpanBeaconInfo;
import com.google.android.things.lowpan.LowpanCredential;
import com.google.android.things.lowpan.LowpanException;
import com.google.android.things.lowpan.LowpanInterface;
import com.google.android.things.lowpan.LowpanInterface.Callback;
import com.google.android.things.lowpan.LowpanManager;
import com.google.android.things.lowpan.LowpanProvisioningParams;
import com.google.android.things.lowpan.LowpanScanner;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class TransmitterActivity extends Activity {
    private static final String TAG = TransmitterActivity.class.getSimpleName();

    // UART parameters for the LoWPAN module
    private static final String UART_PORT = "<ENTER_PORT_NAME>";
    private static final int UART_BAUD = 115200;

    // Network info
    private static final String SERVER_ADDRESS = "<ENTER_IP_ADDRESS>";
    private static final int SERVER_PORT = 23456;
    private static final String LOWPAN_KEY = "FC4262D8F8F79502ABCD326356C610A5";
    private static final String LOWPAN_NETWORK = "lowpan_sample";

    // Strings to display on the segment display
    private static final String DISPLAY_CONN =  "CONN";
    private static final String DISPLAY_READY = "REDY";
    private static final String DISPLAY_WAIT  = "....";
    private static final String DISPLAY_INIT =  "0000";
    private static final String DISPLAY_EMPTY = "    ";
    private static final String DISPLAY_INTER = "XXXX";
    private static final String DISPLAY_ERROR = "ERR!";

    private UartLowpanDriver mLowpanDriver;

    private LowpanManager mLowpanManager;
    private LowpanInterface mLowpanInterface;
    private LowpanScanner mLowpanScanner;

    private ConnectivityManager mConnectivityManager;
    private Network mNetwork;

    private HandlerThread mBackgroundHandlerThread;
    private Handler mHandler;
    private Handler mUiThreadHandler;
    private Socket mSocket;
    private int mSeekBarValue = 0;

    private Button mConnectButton;
    private Button mIncrementButton;
    private Button mDecrementButton;
    private AlphanumericDisplay mSegmentDisplay;

    private android.widget.Button mConnectUiButton;
    private android.widget.Button mDisconnectUiButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender);
        mConnectUiButton = findViewById(R.id.connectButton);
        mDisconnectUiButton = findViewById(R.id.disconnectButton);
        SeekBar seekBar = findViewById(R.id.seekBar);

        mConnectUiButton.setOnClickListener((buttonView) -> connect());
        mDisconnectUiButton.setOnClickListener((buttonView) -> disconnect());
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                        onSeekBarValueChanged(value);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        mUiThreadHandler = new Handler(Looper.getMainLooper());

        mLowpanManager = LowpanManager.getManager();
        try {
            mLowpanManager.registerCallback(mInterfaceCallback);
        } catch (LowpanException e) {
            Log.e(TAG, "Unable to attach LoWPAN callback", e);
        }

        try {
            // Open the segment display
            mSegmentDisplay = RainbowHat.openDisplay();
            mSegmentDisplay.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            mSegmentDisplay.display(DISPLAY_INIT);
            mSegmentDisplay.setEnabled(true);
            onNewValue(DISPLAY_EMPTY);
        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize segment display", e);
        }

        try {
            mConnectButton = RainbowHat.openButtonA();
            mConnectButton.setOnButtonEventListener((button, pressed) -> connect());
            mIncrementButton = RainbowHat.openButtonB();
            mIncrementButton.setOnButtonEventListener((button, pressed) -> { Log.d(TAG, "!!");
                onSeekBarValueChanged(--mSeekBarValue);});
            mDecrementButton = RainbowHat.openButtonC();
            mDecrementButton.setOnButtonEventListener((button, pressed) ->
                onSeekBarValueChanged(++mSeekBarValue));
        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize segment display", e);
        }

        // Initialize network
        resetNetwork();
        try {
            ensureLowpanInterface();
            performNetworkScan();
        } catch (LowpanException e) {
            Log.e(TAG, "Cannot find demo network", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (UART_PORT.contains("ENTER_PORT_NAME")) {
            throw new RuntimeException("You forgot to specify your board's UART port name,"
                    +" please follow the instructions in the README");
        }

        // Register a LoWPAN module connected over UART
        try {
            mLowpanDriver = new UartLowpanDriver(UART_PORT, UART_BAUD);
            mLowpanDriver.register();
        } catch (IOException e) {
            Log.w(TAG, "Unable to init LoWPAN driver");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mLowpanDriver != null) {
            try {
                mLowpanDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close LoWPAN driver");
            } finally {
                mLowpanDriver = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from the network
        disconnect();
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);

        // Detach LoWPAN callbacks
        mLowpanManager.unregisterCallback(mInterfaceCallback);
        if (mLowpanScanner != null) {
            mLowpanScanner.stopNetScan();
            mLowpanScanner.setCallback(null);
            mLowpanScanner = null;
        }

        if (mBackgroundHandlerThread != null) {
            mHandler.removeCallbacksAndMessages(null);
            mBackgroundHandlerThread.quitSafely();
            mBackgroundHandlerThread = null;
        }

        // Close peripheral interfaces
        if (mSegmentDisplay != null) {
            try {
                mSegmentDisplay.close();
            } catch (IOException e) {
                Log.e(TAG, "Cannot close segment display", e);
            } finally {
                mSegmentDisplay = null;
            }
        }
        if (mConnectButton != null) {
            try {
                mConnectButton.close();
                mDecrementButton.close();
                mIncrementButton.close();
            } catch (IOException e) {
                Log.e(TAG, "Cannot close button", e);
            } finally {
                mConnectButton = null;
            }
        }
    }

    /**
     * Initializes the network.
     */
    private void resetNetwork() {
        // Initialize network
        onNoNetwork();
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_LOWPAN)
                .build();
        mBackgroundHandlerThread = new HandlerThread(TAG);
        mBackgroundHandlerThread.start();
        mHandler = new Handler(mBackgroundHandlerThread.getLooper());
        // Make sure that it is connected to a valid network
        mConnectivityManager.registerNetworkCallback(networkRequest,
                mNetworkCallback, mUiThreadHandler);
    }

    /**
     * Callback invoked when the a new network interface appears.
     * This occurs after joining the LoWPAN network.
     */
    private ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (mNetwork == null) {
                        Log.i(TAG, "Got Network: " + network);
                        mNetwork = network;
                        onDisconnected();
                    }
                }
                @Override
                public void onLost(Network network) {
                    if (mNetwork == network) {
                        Log.i(TAG, "Lost Network: " + network);
                        mNetwork = null;
                        onNoNetwork();
                    }
                }
            };

    /**
     * Listen for a new LoWPAN device. This callback is invoked when
     * a LoWPAN module is connected and the user driver is registered.
     */
    private LowpanManager.Callback mInterfaceCallback = new LowpanManager.Callback() {
        @Override
        public void onInterfaceAdded(LowpanInterface lpInterface) {
            try {
                ensureLowpanInterface();
                performNetworkScan();
            } catch (LowpanException e) {
                onNewValue(DISPLAY_ERROR);
                onStatusChanged(e.getMessage());
                Log.e(TAG, "Could not join LoWPAN network", e);
            }
        }

        @Override
        public void onInterfaceRemoved(LowpanInterface lpInterface) {
            Log.w(TAG, "Removed: " + lpInterface.getName());
        }
    };

    /**
     * Verify that a LoWPAN interface is attached
     */
    private void ensureLowpanInterface() throws LowpanException {
        mLowpanInterface = mLowpanManager.getInterface();

        if (mLowpanInterface == null) {
            Log.e(TAG, "No LoWPAN interface found");
            throw new LowpanException(getString(R.string.error_no_lowpan_interface));
        }

        mLowpanInterface.registerCallback(mStateCallback);
    }

    /**
     * Begin a scan for LoWPAN networks nearby
     */
    private void performNetworkScan() throws LowpanException {
        if (mLowpanInterface == null) return;

        // Check if we are already provisioned on the right network
        LowpanProvisioningParams params = mLowpanInterface.getLowpanProvisioningParams(false);
        if (params != null && LOWPAN_NETWORK.equals(params.getLowpanIdentity().getName())) {
            Log.d(TAG, "Already provisioned on the demo network");
            return;
        }

        Log.d(TAG, "Scanning for nearby networks");
        onNewValue(DISPLAY_WAIT);
        mLowpanScanner = mLowpanInterface.createScanner();
        mLowpanScanner.setCallback(mLowpanScannerCallback);
        mLowpanScanner.startNetScan();
    }

    /**
     * Callback to handle network scan results
     */
    private LowpanScanner.Callback mLowpanScannerCallback = new LowpanScanner.Callback() {
        @Override
        public void onNetScanBeacon(LowpanBeaconInfo beacon) {
            if (beacon.getLowpanIdentity().getName().equals(LOWPAN_NETWORK)) {
                joinNetwork(beacon);
            } else {
                Log.i(TAG, "Found network " + beacon.getLowpanIdentity().getName());
            }
        }

        @Override
        public void onScanFinished() {
            Log.i(TAG, "LoWPAN scan complete");
        }
    };

    /**
     * Attempt to join the LoWPAN network
     */
    private void joinNetwork(LowpanBeaconInfo beacon) {
        Log.i(TAG, "Joining demo network");
        LowpanProvisioningParams params = new LowpanProvisioningParams.Builder()
                .setLowpanIdentity(beacon.getLowpanIdentity())
                .setLowpanCredential(LowpanCredential.createMasterKey(LOWPAN_KEY))
                .build();

        try {
            mLowpanInterface.join(params);
        } catch (LowpanException e) {
            Log.e(TAG, "Unable to join LoWPAN network", e);
        }
    }

    /**
     * Callback to react to state changes in the LoWPAN interface
     */
    private LowpanInterface.Callback mStateCallback = new Callback() {
        @Override
        public void onStateChanged(int state) {
            if (state == LowpanInterface.STATE_ATTACHED) {
                Log.d(TAG, "Provisioned on a LoWPAN network");
                onNewValue(DISPLAY_READY);
                onStatusChanged(getString(R.string.ready));
            }
        }

        @Override
        public void onProvisionException(Exception e) {
            Log.e(TAG, "Could not provision network", e);
            onNewValue(DISPLAY_ERROR);
            onStatusChanged(e.getMessage());
        }
    };

    /**
     * Update the Rainbow HAT segment display
     */
    private void onNewValue(String value) {
        mUiThreadHandler.post(() -> {
            try {
                mSegmentDisplay.display(value);
            } catch (IOException e) {
                Log.w(TAG, "Unable to send to segment display " + value, e);
            }
        });
    }

    /**
     * Update the status value in the graphical display
     */
    private void onStatusChanged(String value) {
        mUiThreadHandler.post(() -> {
            TextView statusView = findViewById(R.id.statusView);
            statusView.setText(value);
        });
    }

    /**
     * Connect to other devices on the network.
     */
    private void connect() {
        if (mSocket == null) {
            Log.i(TAG, "connect requested");
            mHandler.post(mConnectRunnable);
        }
    }

    /**
     * Disconnect from other devices on the network.
     */
    private void disconnect() {
        if (mSocket != null) {
            Log.i(TAG, "disconnect requested");
            mHandler.post(mDisconnectRunnable);
        }
    }

    /**
     * Updates the UI when this device is in the process of connecting to the network.
     */
    private void onConnecting() {
        Log.i(TAG, "onConnecting");
        mConnectUiButton.setEnabled(false);
        mDisconnectUiButton.setEnabled(false);

        onStatusChanged(getString(R.string.connecting));
    }

    /**
     * Updates the UI when this device is connected to the network.
     */
    private void onConnected() {
        Log.i(TAG, "onConnected");
        mConnectUiButton.setEnabled(false);
        mDisconnectUiButton.setEnabled(true);

        onNewValue(DISPLAY_CONN);
        onStatusChanged(getString(R.string.connected));
    }

    /**
     * Updates the UI when this device is disconnected from the network.
     */
    private void onDisconnected() {
        Log.i(TAG, "onDisconnected");
        mConnectUiButton.setEnabled(true);
        mDisconnectUiButton.setEnabled(false);

        onStatusChanged(getString(R.string.disconnected));
        onNewValue(DISPLAY_INTER);
    }

    /**
     * Sends a new value of the seekbar to the connected device.
     */
    private void onSeekBarValueChanged(int newValue) {
        onNewValue(String.valueOf(newValue));
        SeekBar seekBar = findViewById(R.id.seekBar);
        mSeekBarValue = newValue;
        seekBar.setProgress(mSeekBarValue);
        // Send a new value to connected devices
        mHandler.post(mUpdateSeekbarRunnable);
    }

    /**
     * Updates the UI when no network has been detected.
     */
    private void onNoNetwork() {
        Log.i(TAG, "No network found");
        mConnectUiButton.setEnabled(false);
        mDisconnectUiButton.setEnabled(false);

        onStatusChanged(getString(R.string.warning_no_network));
    }

    /**
     * Task to connect to a receiver device on the LoWPAN network
     */
    private Runnable mConnectRunnable = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(() -> onConnecting());
            Log.i(TAG, "doInBackground: Connecting...");

            try {
                // Open a connection to the receiving device
                InetAddress serverAddr = mNetwork.getByName(SERVER_ADDRESS);
                // Creating network socket with the receiving device
                Log.i(TAG, "doInBackground: Create socket to " + serverAddr.toString()
                        + " port " + SERVER_PORT);
                mSocket = mNetwork.getSocketFactory().createSocket(serverAddr, SERVER_PORT);
            } catch (UnknownHostException e) {
                // Unable to find the receiving device
                Log.e(TAG, "doInBackground: Host lookup failed.", e);
                e.printStackTrace();
                mSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Connection attempt failed", e);
                e.printStackTrace();
                mSocket = null;
            }

            if (mSocket == null) {
                // Socket creation failed
                runOnUiThread(() -> onDisconnected());
            } else {
                // Socket creation succeeded
                runOnUiThread(() -> onConnected());
            }
        }
    };

    /**
     * Task to disconnect from a receiver device on the LoWPAN network
     */
    private Runnable mDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            // Closing socket
            Log.i(TAG, "doInBackground: Disconnecting...");
            try {
                mSocket.close();
            } catch (IOException x) {
                Log.e(TAG, "Close failed: " + x);
            }
            mSocket = null;
            runOnUiThread(() -> onDisconnected());
        }
    };

    /**
     * Task to send the current value over the LoWPAN network connection
     */
    private Runnable mUpdateSeekbarRunnable = new Runnable() {
        @Override
        public void run() {
            // Retrieve mSeekBarValue as parameter.
            int valueToWrite = mSeekBarValue;
            if (mSocket != null) {
                try {
                    // Send value to the socket
                    mSocket.getOutputStream().write(valueToWrite);
                    Log.i(TAG, "Wrote out value " + valueToWrite);
                } catch (IOException e) {
                    Log.e(TAG, "Exception on write ", e);
                }
            }
        }
    };
}
