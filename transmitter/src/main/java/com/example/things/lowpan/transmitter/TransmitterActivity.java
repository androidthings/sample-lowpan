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
import android.os.AsyncTask;
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
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.lowpan.LowpanCredential;
import com.google.android.things.lowpan.LowpanException;
import com.google.android.things.lowpan.LowpanIdentity;
import com.google.android.things.lowpan.LowpanInterface;
import com.google.android.things.lowpan.LowpanInterface.Callback;
import com.google.android.things.lowpan.LowpanManager;
import com.google.android.things.lowpan.LowpanProvisioningParams;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class TransmitterActivity extends Activity {
    private static final String TAG = TransmitterActivity.class.getSimpleName();

    private ConnectivityManager mConnectivityManager;
    private HandlerThread mBackgroundHandlerThread;
    private Handler mHandler;
    private Handler mUiThreadHandler;
    private Socket mSocket;
    private Network mNetwork;
    private int mSeekBarValue = 0;

    private Button mConnectButton;
    private Button mIncrementButton;
    private Button mDecrementButton;
    private AlphanumericDisplay mSegmentDisplay;

    private android.widget.Button mConnectUiButton;
    private android.widget.Button mDisconnectUiButton;

    // The LoWPAN device address that you want to connect to
    private static final String SERVER_ADDRESS = "fe80::20d:6f00:53b:8022%wpan0";
    private static final int SERVER_PORT = 23456;

    // Strings to display on the segment display
    private static final String DISPLAY_CONN =  "CONN";
    private static final String DISPLAY_READY = "REDY";
    private static final String DISPLAY_WAIT  = "....";
    private static final String DISPLAY_INIT =  "0000";
    private static final String DISPLAY_EMPTY = "    ";
    private static final String DISPLAY_INTER = "XXXX";
    private static final String DISPLAY_ERROR = "ERR!";

    // Network port to listen to
    private static final String LOWPAN_KEY = "FC4262D8F8F79502ABCD326356C610A5";
    private static final String LOWPAN_NETWORK = "lowpan_sample";

    private ConnectivityManager.NetworkCallback mConnectivityManagerNetworkCallback =
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
            mDecrementButton = RainbowHat.openButtonB();
            mDecrementButton.setOnButtonEventListener((button, pressed) -> { Log.d(TAG, "!!");
                onSeekBarValueChanged(--mSeekBarValue);});
            mIncrementButton = RainbowHat.openButtonC();
            mDecrementButton.setOnButtonEventListener((button, pressed) ->
                onSeekBarValueChanged(++mSeekBarValue));
        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize segment display", e);
        }

        mUiThreadHandler = new Handler(Looper.getMainLooper());

        // Initialize network
        resetNetwork();
        try {
            provisionDemoNetwork();
        } catch (LowpanException e) {
            Log.e(TAG, "Cannot provision demo network", e);
        }
    }

    private void provisionDemoNetwork() throws LowpanException {
        LowpanManager lowpanManager = LowpanManager.getManager();
        LowpanInterface lowpanInterface = lowpanManager.getInterface();
        if (lowpanInterface == null) {
            Log.e(TAG, "No LoWPAN interface found");
            onStatusChanged(getString(R.string.error_no_lowpan_interface));
            onNewValue(DISPLAY_ERROR);
            return;
        }

        onNewValue(DISPLAY_WAIT);
        LowpanProvisioningParams params = new LowpanProvisioningParams.Builder()
            .setLowpanIdentity(new LowpanIdentity.Builder()
                .setName(LOWPAN_NETWORK)
                .build())
            .setLowpanCredential(LowpanCredential.createMasterKey(LOWPAN_KEY))
            .build();

        lowpanInterface.form(params);
        lowpanInterface.registerCallback(new Callback() {
            @Override
            public void onStateChanged(int state) {
                if (state == LowpanInterface.STATE_ATTACHED) {
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
        });
    }

    private void onNewValue(String value) {
        mUiThreadHandler.post(() -> {
            try {
                mSegmentDisplay.display(value);
            } catch (IOException e) {
                Log.w(TAG, "Unable to send to segment display " + value, e);
            }
        });
    }

    private void onStatusChanged(String value) {
        mUiThreadHandler.post(() -> {
            TextView statusView = findViewById(R.id.statusView);
            statusView.setText(value);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from the network
        disconnect();
        mConnectivityManager.unregisterNetworkCallback(mConnectivityManagerNetworkCallback);

        if (mBackgroundHandlerThread != null) {
            mHandler.removeCallbacksAndMessages(null);
            mBackgroundHandlerThread.quitSafely();
            mBackgroundHandlerThread = null;
        }

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
                mConnectivityManagerNetworkCallback, mHandler);
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
     *
     * @param newValue The new value of the seekbar to send.
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

    private Runnable mConnectRunnable = new Runnable() {
        @Override
        public void run() {
            onConnecting();
            Log.i(TAG, "doInBackground: Connecting...");

            try {
                // Open a connection to the receiving device
                InetAddress serverAddr = mNetwork.getByName(SERVER_ADDRESS);
                if (serverAddr == null) {
                    // Unable to find the receiving device
                    Log.e(TAG, "doInBackground: Host lookup failed.");
                    mSocket = null;
                } else {
                    // Creating network socket with the receiving device
                    Log.i(TAG, "doInBackground: Create socket to " + serverAddr.toString()
                        + " port " + SERVER_PORT);
                    mSocket = mNetwork.getSocketFactory().createSocket(serverAddr, SERVER_PORT);
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection attempt failed", e);
                mSocket = null;
            }

            if (mSocket == null) {
                // Socket creation failed
                onDisconnected();
            } else {
                // Socket creation succeeded
                onConnected();
            }
        }
    };

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
            onDisconnected();
        }
    };

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
