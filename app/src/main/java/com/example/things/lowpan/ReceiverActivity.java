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

package com.example.things.lowpan;

import android.app.Activity;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.ht16k33.Ht16k33;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ReceiverActivity extends Activity {
    private static final String TAG = ReceiverActivity.class.getSimpleName();

    // Strings to display on the segment display
    private static final String DISPLAY_CONN =  "CONN";
    private static final String DISPLAY_READY = "REDY";
    private static final String DISPLAY_INIT =  "0000";
    private static final String DISPLAY_EMPTY = "    ";
    private static final String DISPLAY_INTER = "XXXX";
    // Network port to listen to
    private static final int SERVER_PORT = 23456;

    private ServerSocket mServerSocket;
    private Handler mHandler;
    private Thread mServerThread;
    private Thread mSocketThread;
    private AlphanumericDisplay mSegmentDisplay;
    private TextView mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);
        mStatus = findViewById(R.id.lowpan_status);

        mHandler = new Handler();
        try {
            // Open the segment display
            mSegmentDisplay = RainbowHat.openDisplay();
            mSegmentDisplay.setBrightness(Ht16k33.HT16K33_BRIGHTNESS_MAX);
            mSegmentDisplay.display(DISPLAY_INIT);
            mSegmentDisplay.setEnabled(true);
            onNewValue(DISPLAY_READY);
        } catch (IOException e) {
            Log.e(TAG, "Unable to initialize segment display", e);
        }
        onStatusChanged(getString(R.string.ready));
        // Start a new thread to handle network events
        mServerThread = new Thread(new ServerThread());
        mServerThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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