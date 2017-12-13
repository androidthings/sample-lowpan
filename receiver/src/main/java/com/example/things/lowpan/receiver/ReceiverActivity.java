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

package com.example.things.lowpan.receiver;

import android.app.Activity;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.contrib.driver.lowpan.UartLowpanDriver;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.lowpan.LowpanCredential;
import com.google.android.things.lowpan.LowpanException;
import com.google.android.things.lowpan.LowpanIdentity;
import com.google.android.things.lowpan.LowpanInterface;
import com.google.android.things.lowpan.LowpanInterface.Callback;
import com.google.android.things.lowpan.LowpanManager;
import com.google.android.things.lowpan.LowpanProvisioningParams;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ReceiverActivity extends Activity {
    private static final String TAG = ReceiverActivity.class.getSimpleName();

    // UART parameters for the LoWPAN module
    private static final String UART_PORT = "<ENTER_PORT_NAME>";
    private static final int UART_BAUD = 115200;

    // Network info
    private static final int SERVER_PORT = 23456;
    private static final String LOWPAN_KEY = "FC4262D8F8F79502ABCD326356C610A5";
    private static final String LOWPAN_NETWORK = "lowpan_sample";

    // Strings to display on the segment display
    private static final String DISPLAY_CONN =  "CONN";
    private static final String DISPLAY_READY = "REDY";
    private static final String DISPLAY_INIT =  "0000";
    private static final String DISPLAY_EMPTY = "    ";
    private static final String DISPLAY_INTER = "XXXX";
    private static final String DISPLAY_ERROR = "ERR!";
    private static final String DISPLAY_WAIT  = "WAIT";

    private UartLowpanDriver mLowpanDriver;

    private ServerSocket mServerSocket;
    private Handler mHandler;
    private Thread mServerThread;
    private Thread mSocketThread;
    private AlphanumericDisplay mSegmentDisplay;
    private TextView mStatus;

    private LowpanManager mLowpanManager;
    private LowpanInterface mLowpanInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);
        mStatus = findViewById(R.id.lowpan_status);

        mLowpanManager = LowpanManager.getManager();
        try {
            mLowpanManager.registerCallback(mInterfaceCallback);
        } catch (LowpanException e) {
            Log.e(TAG, "Unable to attach LoWPAN callback", e);
        }

        mHandler = new Handler();
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
            ensureLowpanInterface();
            formNetwork();
        } catch (LowpanException e) {
            onNewValue(DISPLAY_ERROR);
            onStatusChanged(e.getMessage());
            Log.e(TAG, "Unable to form LoWPAN network", e);
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

        // Detach LoWPAN callbacks
        mLowpanManager.unregisterCallback(mInterfaceCallback);
        if (mLowpanInterface != null) {
            mLowpanInterface.unregisterCallback(mStateCallback);
            mLowpanInterface = null;
        }

        // Close the server thread
        if (mServerThread != null) {
            mServerThread.interrupt();
            mServerThread = null;
        }
        // Close network socket
        try {
            mServerSocket.close();
            mServerSocket = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Close segment display
        try {
            mSegmentDisplay.display(DISPLAY_EMPTY);
            mSegmentDisplay.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to close segment display", e);
        } finally {
            mSegmentDisplay = null;
        }
    }

    /**
     * Listen for a new LoWPAN device. This callback is invoked when
     * a LoWPAN module is connected and the user driver is registered.
     */
    private LowpanManager.Callback mInterfaceCallback = new LowpanManager.Callback() {
        @Override
        public void onInterfaceAdded(LowpanInterface lpInterface) {
            try {
                ensureLowpanInterface();
                formNetwork();
            } catch (LowpanException e) {
                onNewValue(DISPLAY_ERROR);
                onStatusChanged(e.getMessage());
                Log.e(TAG, "Unable to form LoWPAN network", e);
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
     * Create a new LoWPAN network, if necessary
     */
    private void formNetwork() throws LowpanException {
        if (mLowpanInterface == null) return;

        // Check if we are already provisioned on the right network
        LowpanProvisioningParams params = mLowpanInterface.getLowpanProvisioningParams(false);
        if (params != null && LOWPAN_NETWORK.equals(params.getLowpanIdentity().getName())) {
            Log.d(TAG, "Already provisioned on the demo network");
            return;
        }

        Log.i(TAG, "Forming demo network");
        onNewValue(DISPLAY_WAIT);
        params = new LowpanProvisioningParams.Builder()
                .setLowpanIdentity(new LowpanIdentity.Builder()
                        .setName(LOWPAN_NETWORK)
                        .build())
                .setLowpanCredential(LowpanCredential.createMasterKey(LOWPAN_KEY))
                .build();

        mLowpanInterface.form(params);
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

                // Start a new thread to handle network events
                mServerThread = new Thread(new ServerThread());
                mServerThread.start();
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
     * Callback that is run when a new device connects to this device.
     *
     * @param socket The network socket that they are connected to.
     */
    private void onNewConnection(Socket socket) {
        // A new device is connected
        onNewValue(DISPLAY_CONN);
        onStatusChanged(getString(R.string.connected));
        if (mSocketThread != null) {
            mSocketThread.interrupt();
            mSocketThread = null;
        }
        mSocketThread = new Thread(new SocketThread(socket));
        mSocketThread.start();
    }

    /**
     * Callback that is run when a new value is received from another device.
     *
     * @param value The value that was received.
     */
    private void onNewValue(String value) {
        mHandler.post(() -> {
            try {
                // Update the value on the segment display
                mSegmentDisplay.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
                mSegmentDisplay.display(value);
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "Unable to change segment display", e);
            }
        });
    }

    /**
     * Callback that is run when the connection status has changed, to update the UI.
     *
     * @param status The new connection status.
     */
    private void onStatusChanged(String status) {
        mHandler.post(() -> {
            // Update the connection status on the TextView
            mStatus.setText(status);
        });
    }

    /**
     * Task to accept incoming socket connections.
     */
    private class ServerThread implements Runnable {
        @Override
        public void run() {
            try {
                // Connect to socket
                mServerSocket = new ServerSocket(SERVER_PORT);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start server socket", e);
                return;
            }

            Log.i(TAG, "Listening for incoming connections");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = mServerSocket.accept();
                    onNewConnection(socket);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start new socket connection", e);
                }
            }
            if (Thread.currentThread() == mServerThread) {
                mServerThread = null;
            }
        }
    }

    /**
     * Task to receive data on a given connection
     */
    private class SocketThread implements Runnable {
        private final Socket mSocket;

        SocketThread(Socket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // A new message is received on the network
                    int value = mSocket.getInputStream().read();
                    String receivedMessage = Integer.toString(value);
                    if (value >= 0) {
                        // Display the value on the segment display
                        onNewValue(receivedMessage);
                        // Display the value on the screen
                        mHandler.post(() -> {
                            TextView textView = findViewById(R.id.lowpan_message);
                            textView.setText(receivedMessage);
                        });
                    } else {
                        break;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading from socket", e);
                    break;
                }
            }

            // Thread was interrupted
            onNewValue(DISPLAY_INTER);
            onStatusChanged(getString(R.string.socket_interrupted));
            if (Thread.currentThread() == mSocketThread) {
                mSocketThread = null;
            }
        }
    }
}