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
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class SenderActivity extends Activity {
    private static final String TAG = SenderActivity.class.getSimpleName();

    private ConnectivityManager mConnectivityManager;
    private Handler mHandler;
    private Socket mSocket;
    private Network mNetwork;
    private UpdateSeekBarTask mUpdateSeekBarTask;
    private int mSeekBarValue = 0;

    // The LoWPAN device address that you want to connect to
    private static final String SERVER_ADDRESS = "<DEVICE_SERVER_ADDRESS>";
    private static final int SERVER_PORT = 23456;

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
        Button connectButton = findViewById(R.id.connectButton);
        Button disconnectButton = findViewById(R.id.disconnectButton);
        SeekBar seekBar = findViewById(R.id.seekBar);

        connectButton.setOnClickListener((buttonView) -> connect());
        disconnectButton.setOnClickListener((buttonView) -> disconnect());
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        if (b) {
                            // When the seekbar is changed, send the new value
                            onSeekBarValueChanged(i);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        // Initialize network
        resetNetwork();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect from the network
        disconnect();
        mConnectivityManager.unregisterNetworkCallback(mConnectivityManagerNetworkCallback);
    }

    /**
     * Connect to other devices on the network.
     */
    private void connect() {
        if (mSocket == null) {
            Log.i(TAG, "connect requested");
            new ConnectTask().execute();
        }
    }

    /**
     * Disconnect from other devices on the network.
     */
    private void disconnect() {
        if (mSocket != null) {
            Log.i(TAG, "disconnect requested");
            new DisconnectTask().execute();
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
        mHandler = new Handler();
        // Make sure that it is connected to a valid network
        mConnectivityManager.registerNetworkCallback(networkRequest,
                mConnectivityManagerNetworkCallback, mHandler);
    }

    /**
     * Updates the UI when this device is in the process of connecting to the network.
     */
    private void onConnecting() {
        Log.i(TAG, "onConnecting");
        Button connectButton = findViewById(R.id.connectButton);
        connectButton.setEnabled(false);

        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setEnabled(false);

        TextView statusView = findViewById(R.id.statusView);
        statusView.setText(R.string.connecting);
    }

    /**
     * Updates the UI when this device is connected to the network.
     */
    private void onConnected() {
        Log.i(TAG, "onConnected");
        Button connectButton = findViewById(R.id.connectButton);
        connectButton.setEnabled(false);

        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setEnabled(true);

        TextView statusView = findViewById(R.id.statusView);
        statusView.setText(R.string.connected);
    }

    /**
     * Updates the UI when this device is disconnected from the network.
     */
    private void onDisconnected() {
        Log.i(TAG, "onDisconnected");
        Button connectButton = findViewById(R.id.connectButton);
        connectButton.setEnabled(true);

        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setEnabled(false);

        TextView statusView = findViewById(R.id.statusView);
        statusView.setText(R.string.disconnected);
    }

    /**
     * Sends a new value of the seekbar to the connected device.
     *
     * @param newValue The new value of the seekbar to send.
     */
    private void onSeekBarValueChanged(int newValue) {
        if (mSeekBarValue != newValue) {
            mSeekBarValue = newValue;
            if (mUpdateSeekBarTask == null) {
                mUpdateSeekBarTask = new UpdateSeekBarTask();
                // Send a new value to connected devices
                mUpdateSeekBarTask.execute(mSeekBarValue);
            }
        }
    }

    /**
     * Updates the UI when no network has been detected.
     */
    private void onNoNetwork() {
        Log.i(TAG, "No network found");
        Button connectButton = findViewById(R.id.connectButton);
        connectButton.setEnabled(false);

        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setEnabled(false);

        TextView statusView = findViewById(R.id.statusView);
        statusView.setText(R.string.warning_no_network);
    }

    private class ConnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            onConnecting();
        }

        @Override
        protected Void doInBackground(Void... params) {
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
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mSocket == null) {
                // Socket creation failed
                onDisconnected();
            } else {
                // Socket creation succeeded
                onConnected();
            }
        }
    }

    private class DisconnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            // Closing socket
            Log.i(TAG, "doInBackground: Disconnecting...");
            try {
                mSocket.close();
            } catch (IOException x) {
                Log.e(TAG, "Close failed: " + x);
            }
            mSocket = null;
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            onDisconnected();
        }
    }

    private class UpdateSeekBarTask extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... params) {
            // Retrieve mSeekBarValue as parameter.
            int valueToWrite = params[0];
            if (mSocket != null) {
                try {
                    // Send value to the socket
                    mSocket.getOutputStream().write(valueToWrite);
                    mUpdateSeekBarTask = null;
                    Log.i(TAG, "Wrote out value " + valueToWrite);
                } catch (IOException e) {
                    Log.e(TAG, "Exception on write ", e);
                }
            }
            return null;
        }
    }
}
