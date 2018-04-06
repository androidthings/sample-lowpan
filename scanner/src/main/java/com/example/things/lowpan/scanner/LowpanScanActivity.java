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

package com.example.things.lowpan.scanner;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.things.contrib.driver.lowpan.UartLowpanDriver;
import com.google.android.things.lowpan.LowpanBeaconInfo;
import com.google.android.things.lowpan.LowpanCredential;
import com.google.android.things.lowpan.LowpanException;
import com.google.android.things.lowpan.LowpanIdentity;
import com.google.android.things.lowpan.LowpanInterface;
import com.google.android.things.lowpan.LowpanManager;
import com.google.android.things.lowpan.LowpanProvisioningParams;
import com.google.android.things.lowpan.LowpanRuntimeException;
import com.google.android.things.lowpan.LowpanScanner;

import java.io.IOException;
import java.util.Random;

public class LowpanScanActivity extends Activity implements AdapterView.OnItemClickListener {
    private static final String TAG = LowpanScanActivity.class.getSimpleName();

    // UART parameters for the LoWPAN module
    private static final String UART_PORT = "<ENTER_PORT_NAME>";
    private static final int UART_BAUD = 115200;

    // Network info
    private static final String LOWPAN_KEY = "FC4262D8F8F79502ABCD326356C610A5";

    private LowpanManager mLowpanManager = LowpanManager.getInstance();
    private LowpanInterface mLowpanInterface = null;
    private LowpanScanner mLowpanScanner = null;
    private UartLowpanDriver mLowpanDriver;

    private Button mScanButton;
    private TextView mInterfaceStatus, mNetworkStatus;
    private LowpanBeaconAdapter mBeaconsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lowpan_scan);

        mScanButton = findViewById(R.id.scanButton);
        mScanButton.setOnClickListener((view) -> beginScan());

        Button provisionButton = findViewById(R.id.provisionButton);
        provisionButton.setOnClickListener(view -> createNewNetwork());

        Button leaveButton = findViewById(R.id.leaveButton);
        leaveButton.setOnClickListener(view -> leaveNetwork());

        ListView beaconsView = findViewById(R.id.beacons);
        beaconsView.setOnItemClickListener(this);
        mBeaconsAdapter = new LowpanBeaconAdapter(this);
        beaconsView.setAdapter(mBeaconsAdapter);

        mInterfaceStatus = findViewById(R.id.interfaceStatus);
        mNetworkStatus = findViewById(R.id.networkStatus);

        try {
            mLowpanManager.registerCallback(mInterfaceCallback);
        } catch (LowpanException e) {
            Log.e(TAG, "Unable to attach LoWPAN callback");
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
            verifyLowpanInterface();
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

        if (mLowpanInterface != null) {
            mLowpanInterface.unregisterCallback(mStateCallback);
            mLowpanInterface = null;
        }

        mLowpanManager.unregisterCallback(mInterfaceCallback);
    }

    /**
     * Listen for a new LoWPAN device. This callback is invoked when
     * a LoWPAN module is connected and the user driver is registered.
     */
    private LowpanManager.Callback mInterfaceCallback = new LowpanManager.Callback() {
        @Override
        public void onInterfaceAdded(LowpanInterface lpInterface) {
            Log.d(TAG, "Added: " + lpInterface.getName());
            verifyLowpanInterface();
        }

        @Override
        public void onInterfaceRemoved(LowpanInterface lpInterface) {
            Log.w(TAG, "Removed: " + lpInterface.getName());
            verifyLowpanInterface();
        }
    };

    /**
     * Initialize the scanner once a LoWPAN interface has been detected.
     */
    private void verifyLowpanInterface() {
        mLowpanInterface = mLowpanManager.getInterface();

        if (mLowpanInterface == null) {
            Log.e(TAG, "No LoWPAN interface found");
            mInterfaceStatus.setText(R.string.error_no_lowpan_interface);
            mScanButton.setEnabled(false);
            return;
        }
        mLowpanInterface.registerCallback(mStateCallback);

        mLowpanScanner = mLowpanInterface.createScanner();
        mLowpanScanner.setCallback(new Handler(Looper.getMainLooper()), mScanCallback);

        mInterfaceStatus.setText(R.string.ready_to_scan);
        mScanButton.setEnabled(true);
    }

    /**
     * Start to scan for nearby LoWPAN networks.
     */
    private void beginScan() {
        // Empty the list
        mBeaconsAdapter.clear();

        // Start scanning for networks
        try {
            mLowpanScanner.startNetScan();
            Log.d(TAG, "Scanning for networks...");
            mInterfaceStatus.setText(R.string.progress_scanning);
        } catch (LowpanException e) {
            mInterfaceStatus.setText(R.string.error_scan_failed);
            Log.e(TAG, "Scan failed", e);
            return;
        }

        // Disable button until scan is complete
        mScanButton.setEnabled(false);
    }

    /**
     * A callback that is run when the scan is complete.
     */
    private void scanComplete() {
        // Network scan is complete
        mInterfaceStatus.setText(R.string.scan_finished);
        Log.d(TAG, "Scan Finished");

        // Enable the scan button again
        mScanButton.setEnabled(true);
    }

    /**
     * Add a newly discovered LoWPAN network to the list of nearby LoWPAN networks.
     *
     * @param beacon The LoWPAN network that was discovered.
     */
    private void addDiscoveredBeacon(LowpanBeaconInfo beacon) {
        // Display information about each beacon when it is discovered.
        mBeaconsAdapter.add(beacon);
        Log.d(TAG, "Added beacon: " + beacon.toString());
    }

    /**
     * Handle results when new networks are detected by the scanner.
     */
    private LowpanScanner.Callback mScanCallback = new LowpanScanner.Callback() {
        @Override
        public void onNetScanBeacon(LowpanBeaconInfo beacon) {
            // When a new LoWPAN network is found, add to the list
            addDiscoveredBeacon(beacon);
        }

        @Override
        public void onScanFinished() {
            scanComplete();
        }
    };

    /**
     * Called when a list item containing a LoWPAN network is selected
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        LowpanBeaconInfo beacon = mBeaconsAdapter.getItem(position);
        joinNetwork(beacon);
    }

    /**
     * Form a new network with a randomly-generated name and connect to it.
     */
    private void createNewNetwork() {
        Random random = new Random();
        // Generate a new network name that is easily unique, ie. "LoWPAN_142"
        String name = "LoWPAN_" + Integer.toString(random.nextInt(1000));
        LowpanProvisioningParams params = new LowpanProvisioningParams.Builder()
                .setLowpanIdentity(new LowpanIdentity.Builder()
                    .setName(name)
                    .build())
                .setLowpanCredential(LowpanCredential.createMasterKey(LOWPAN_KEY))
                .build();
        try {
            // Try to form the network. The callback will handle the success/error states.
            Log.d(TAG, "Creating new LoWPAN network with name " + name);
            mInterfaceStatus.setText(getString(R.string.status_new_network, name));
            mLowpanInterface.form(params);
        } catch (LowpanException | LowpanRuntimeException e) {
            // Something happened which prevents the network formation
            Log.e(TAG, "Unable to create new network", e);
            mInterfaceStatus.setText(getString(R.string.status_network_error,
                    e.getMessage()));
        }
    }

    /**
     * Connect to the provided LoWPAN network
     * @param beacon Beacon containing the network identity
     */
    private void joinNetwork(LowpanBeaconInfo beacon) {
        // Try to join a network with a particular name and a standard key
        LowpanProvisioningParams params = new LowpanProvisioningParams.Builder()
                .setLowpanIdentity(beacon.getLowpanIdentity())
                .setLowpanCredential(LowpanCredential.createMasterKey(LOWPAN_KEY))
                .build();

        // Try to join the network. The callback will handle the success/error states.
        try {
            mLowpanInterface.join(params);
        } catch (LowpanException e) {
            Log.e(TAG, "Unable to join network", e);
            mInterfaceStatus.setText(getString(R.string.status_network_error,
                    e.getMessage()));
        }
    }

    /**
     * Disaccociate the LoWPAN interface with the current network
     */
    private void leaveNetwork() {
        try {
            mLowpanInterface.leave();
        } catch (LowpanException e) {
            Log.e(TAG, "Unable to leave network", e);
            mInterfaceStatus.setText(getString(R.string.status_network_error,
                    e.getMessage()));
        }
    }

    /**
     * Handle interface state changes while attempting to create a network
     */
    private LowpanInterface.Callback mStateCallback = new LowpanInterface.Callback() {
        @Override
        public void onStateChanged(int state) {
            mInterfaceStatus.setText(Utils.stateToString(state));
        }

        @Override
        public void onLowpanIdentityChanged(LowpanIdentity identity) {
            if (identity == null) {
                mNetworkStatus.setText(null);
            } else {
                mNetworkStatus.setText(identity.getName());
            }
        }

        @Override
        public void onProvisionException(Exception e) {
            // Something happened which prevents the network provisioning
            Log.e(TAG, "Unable to provision network interface", e);
            mInterfaceStatus.setText(getString(R.string.status_network_error,
                    e.getMessage()));
        }
    };
}
