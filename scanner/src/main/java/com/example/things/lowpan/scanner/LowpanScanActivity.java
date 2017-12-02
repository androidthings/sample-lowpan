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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.things.lowpan.LowpanBeaconInfo;
import com.google.android.things.lowpan.LowpanCredential;
import com.google.android.things.lowpan.LowpanException;
import com.google.android.things.lowpan.LowpanIdentity;
import com.google.android.things.lowpan.LowpanInterface;
import com.google.android.things.lowpan.LowpanManager;
import com.google.android.things.lowpan.LowpanProvisioningParams;
import com.google.android.things.lowpan.LowpanRuntimeException;
import com.google.android.things.lowpan.LowpanScanner;

import java.util.Random;

public class LowpanScanActivity extends Activity {
    private static final String TAG = LowpanScanActivity.class.getSimpleName();
    private static final String LOWPAN_KEY = "FC4262D8F8F79502ABCD326356C610A5";

    private LowpanManager mLowpanManager = LowpanManager.getManager();
    private LowpanInterface mLowpanInterface = null;
    private LowpanScanner mLowpanScanner = null;
    private TextView mScanStatus;

    private ProgressDialog mProgressDialog;
    private LowpanInterface.Callback mFormCallback = new LowpanInterface.Callback() {
        @Override
        public void onStateChanged(int state) {
            // Check that the next state is STATE_ATTACHED
            if (state == LowpanInterface.STATE_ATTACHED) {
                // This callback is no longer needed
                mLowpanInterface.unregisterCallback(mFormCallback);
                mScanStatus.setText(getString(R.string.formed_network));
            }
        }

        @Override
        public void onProvisionException(Exception e) {
            // Something happened which prevents the network formation
            Log.e(TAG, "Unable to create new network", e);
            mScanStatus.setText(getString(R.string.status_new_network_error,
                    e.getMessage()));
        }
    };
    private LowpanInterface.Callback mJoinCallback = new LowpanInterface.Callback() {
        @Override
        public void onStateChanged(int state) {
            if (state == LowpanInterface.STATE_ATTACHED) {
                // We have successfully connected
                mProgressDialog.hide();
                mProgressDialog.dismiss();
                // This callback is no longer needed
                mLowpanInterface.unregisterCallback(mJoinCallback);
                mScanStatus.setText(getString(R.string.joined_network));
            }
        }

        @Override
        public void onProvisionException(Exception e) {
            // An error occurred during joining
            Log.e(TAG, "Join failed.", e);
            new AlertDialog.Builder(LowpanScanActivity.this)
                    .setTitle(getString(R.string.error_join_title))
                    .setMessage(e.getMessage())
                    .show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lowpan_scan);

        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener((view) -> onBeginScan());

        Button provisionButton = findViewById(R.id.provisionButton);
        provisionButton.setOnClickListener(view -> onCreateNewNetwork());

        mScanStatus = findViewById(R.id.scanStatus);

        mLowpanInterface = mLowpanManager.getInterface();

        if (mLowpanInterface == null) {
            Log.e(TAG, "No LoWPAN interface found");
            mScanStatus.setText(R.string.error_no_lowpan_interface);
            scanButton.setClickable(false);
            return;
        }
        mLowpanScanner = mLowpanInterface.createScanner();

        mLowpanScanner.setCallback(
                new LowpanScanner.Callback() {
                    @Override
                    public void onNetScanBeacon(LowpanBeaconInfo beacon) {
                        // When a new LoWPAN network is found, add to the list
                        LowpanScanActivity.this.onNetScanBeacon(beacon);
                    }

                    @Override
                    public void onScanFinished() {
                        LowpanScanActivity.this.onScanFinished();
                    }
                },
                new Handler());
        mScanStatus.setText(R.string.ready_to_scan);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Start to scan for nearby LoWPAN networks that this device can join.
     */
    private void onBeginScan() {
        // Start scanning for networks
        LinearLayout beaconsView = findViewById(R.id.beacons);
        // Empty the list
        beaconsView.removeAllViews();

        TextView scanStatus = findViewById(R.id.scanStatus);
        try {
            mLowpanScanner.startNetScan();
            Log.d(TAG, "Scanning for networks...");
            scanStatus.setText(R.string.progress_scanning);

            if (mProgressDialog == null) {
                mProgressDialog = new ProgressDialog(this);
            }
            mProgressDialog.setMessage(getString(R.string.progress_scanning));
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        } catch (LowpanException e) {
            scanStatus.setText(R.string.error_scan_failed);
            Log.e(TAG, "Scan failed", e);
            return;
        }
        // Disable button until scan is complete
        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setClickable(false);
    }

    /**
     * Form a new network with a randomly-generated name and connect to it.
     */
    private void onCreateNewNetwork() {
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
            mScanStatus.setText(getString(R.string.status_new_network, name));
            mLowpanInterface.form(params);
            mLowpanInterface.registerCallback(mFormCallback);
        } catch (LowpanException | LowpanRuntimeException e) {
            // Something happened which prevents the network formation
            Log.e(TAG, "Unable to create new network", e);
            mScanStatus.setText(getString(R.string.status_new_network_error, e.getMessage()));
        }
    }

    /**
     * Add a newly discovered LoWPAN network to the list of nearby LoWPAN networks.
     *
     * @param beacon The LoWPAN network that was discovered.
     */
    private void onNetScanBeacon(LowpanBeaconInfo beacon) {
        // Display information about each beacon when it is discovered.
        LinearLayout beaconsView = findViewById(R.id.beacons);
        LayoutInflater inflater = getLayoutInflater();
        View child = inflater.inflate(R.layout.list_item_beacon, beaconsView, false);

        TextView networkNameView = child.findViewById(R.id.networkName);
        networkNameView.setText(beacon.getLowpanIdentity().getName());

        TextView xpanidView = child.findViewById(R.id.xpanid);
        xpanidView.setText(Utils.bytesToHex(beacon.getLowpanIdentity().getXpanid()));

        TextView chanView = child.findViewById(R.id.chanValue);
        chanView.setText(String.format("%d", beacon.getLowpanIdentity().getChannel()));

        TextView panidView = child.findViewById(R.id.panidValue);
        panidView.setText(String.format("%04X", beacon.getLowpanIdentity().getPanid()));

        TextView macAddrView = child.findViewById(R.id.macaddr);
        macAddrView.setText(Utils.bytesToAddrHex(beacon.getBeaconAddress()));

        ProgressBar rssiProgressView = child.findViewById(R.id.rssiProgress);
        int rssi = Utils.rssiToLqi(beacon.getRssi());
        rssiProgressView.setProgress(rssi);

        ProgressBar lqiProgressView = child.findViewById(R.id.lqiProgress);
        int lqi = beacon.getLqi();
        lqiProgressView.setProgress(lqi);

        View canAssistView = child.findViewById(R.id.canAssist);
        canAssistView.setVisibility(beacon.isFlagSet(LowpanBeaconInfo.FLAG_CAN_ASSIST) ?
                View.VISIBLE : View.GONE);

        Button joinButton = child.findViewById(R.id.joinButton);
        joinButton.setOnClickListener((view) -> onJoinBeaconInfo(beacon));

        beaconsView.addView(child);
        Log.d(TAG, "Added beacon: " + beacon.toString());
    }

    /**
     * Attempt to join a particular LoWPAN network.
     *
     * @param beaconInfo The LoWPAN network to connect to.
     */
    private void onJoinBeaconInfo(LowpanBeaconInfo beaconInfo) {
        // Try to join a network with a particular name and a standard key
        LowpanProvisioningParams params = new LowpanProvisioningParams.Builder()
                .setLowpanIdentity(beaconInfo.getLowpanIdentity())
                .setLowpanCredential(LowpanCredential.createMasterKey(LOWPAN_KEY))
                .build();
        // Display a progress dialog while trying to join the network
        mProgressDialog = new ProgressDialog(LowpanScanActivity.this);
        mProgressDialog.setMessage(getString(R.string.progress_joining));
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();

        // Join the network and register a callback to handle the response
        try {
            mLowpanInterface.join(params);
            mLowpanInterface.registerCallback(mJoinCallback);
        } catch (LowpanException e) {
            Log.e(TAG, "Join failed.", e);
        }
    }

    /**
     * A callback that is run when the scan is complete.
     */
    private void onScanFinished() {
        // Network scan is complete
        TextView scanStatus = findViewById(R.id.scanStatus);
        scanStatus.setText(R.string.scan_finished);
        Log.d(TAG, "Scan Finished");
        if (mProgressDialog != null) {
            mProgressDialog.hide();
        }
        // Enable the scan button again
        Button scanButton = findViewById(R.id.scanButton);
        scanButton.setClickable(true);
    }
}
