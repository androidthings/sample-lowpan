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
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.things.lowpan.LowpanException;
import com.google.android.things.lowpan.LowpanIdentity;
import com.google.android.things.lowpan.LowpanInterface;
import com.google.android.things.lowpan.LowpanManager;
import com.google.android.things.lowpan.LowpanProvisioningParams;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private LowpanManager mLowpanManager = LowpanManager.getManager();
    private LowpanInterface mLowpanInterface;
    private DemoLowpanManagerCallback mLowpanManagerCallback;
    private DemoLowpanInterfaceCallback mLowpanInterfaceCallback;
    private LowpanInterface.Callback mLeaveCallback = new LowpanInterface.Callback() {
        @Override
        public void onStateChanged(int state) {
            if (state == LowpanInterface.STATE_OFFLINE) {
                // Successfully left the network
                refreshValues();
            }
        }

        @Override
        public void onProvisionException(Exception e) {
            Log.e(TAG, "Unable to leave network", e);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Setup UI elements
        Button scanButton = findViewById(R.id.scanButton);
        Button leaveButton = findViewById(R.id.leaveButton);
        Button demoButton = findViewById(R.id.sendButton);
        Button receiverButton = findViewById(R.id.receiverButton);
        CheckBox enabledCheckbox = findViewById(R.id.isEnabled);

        // Set events to each UI element when a click occurs
        scanButton.setOnClickListener((buttonView) -> onScanActivity());
        demoButton.setOnClickListener((buttonView) -> onSenderActivity());
        receiverButton.setOnClickListener((buttonView) -> onReceiverActivity());
        leaveButton.setOnClickListener((buttonView) -> onLeave());
        enabledCheckbox.setOnCheckedChangeListener(this::onEnabledChanged);

        mLowpanManagerCallback = new DemoLowpanManagerCallback();
        mLowpanInterfaceCallback = new DemoLowpanInterfaceCallback();
    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            mLowpanManager.registerCallback(mLowpanManagerCallback);
        } catch (LowpanException e) {
            e.printStackTrace();
        }

        LowpanInterface lowpanInterface = mLowpanManager.getInterface();
        if (lowpanInterface != null) {
            onLowpanInterfaceAdded(lowpanInterface);
        } else {
            Log.e(TAG, "No LoWPAN interfaces found");
        }

        refreshValues();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mLowpanInterface != null) {
            mLowpanInterface.unregisterCallback(mLowpanInterfaceCallback);
        }
        mLowpanManager.unregisterCallback(mLowpanManagerCallback);
    }

    private void onScanActivity() {
        // Start scanning for networks
        Intent intent = new Intent(MainActivity.this, LowpanScanActivity.class);
        startActivity(intent);
    }

    private void onSenderActivity() {
        // Start sending data to another device
        Intent intent = new Intent(MainActivity.this, SenderActivity.class);
        startActivity(intent);
    }

    private void onReceiverActivity() {
        // Start receiving data from another device
        Intent intent = new Intent(MainActivity.this, ReceiverActivity.class);
        startActivity(intent);
    }

    private void onLeave() {
        try {
            // Leave the current network
            mLowpanInterface.leave();
            // Register callback to handle success or failure
            mLowpanInterface.registerCallback(mLeaveCallback);
        } catch (LowpanException e) {
            Log.e(TAG, "Leaving LoWPAN network failed", e);
        }
        refreshValues();
    }

    private void onEnabledChanged(CompoundButton checkBox, boolean value) {
        if (mLowpanInterface != null) {
            if (value != mLowpanInterface.isEnabled()) {
                try {
                    mLowpanInterface.setEnabled(value);
                } catch (LowpanException e) {
                    Log.e(TAG, "Disable/Enable failed.", e);
                    checkBox.setChecked(!value);
                }
            }
        }
    }

    private void refreshValues() {
        // Change the values displayed in the UI
        TextView ifaceName = findViewById(R.id.ifaceName);
        TextView stateView = findViewById(R.id.state);
        TextView roleView = findViewById(R.id.role);
        TextView networkNameView = findViewById(R.id.networkName);
        CheckBox enabledView = findViewById(R.id.isEnabled);
        Button scanButton = findViewById(R.id.scanButton);
        Button leaveButton = findViewById(R.id.leaveButton);
        Button sendButton = findViewById(R.id.sendButton);

        if (mLowpanInterface != null) {
            // A LoWPAN interface was found for this device
            ifaceName.setText(mLowpanInterface.getName());
            stateView.setText(Utils.stateToString(mLowpanInterface.getState()));
            roleView.setText(Utils.roleToString(mLowpanInterface.getRole()));
            enabledView.setEnabled(true);

            if (mLowpanInterface.isEnabled()) {
                // The LoWPAN interface is enabled
                enabledView.setChecked(true);
                scanButton.setEnabled(true);
                sendButton.setEnabled(mLowpanInterface.getState() == LowpanInterface.STATE_ATTACHED);

                if (mLowpanInterface.isProvisioned()) {
                    // The LoWPAN interface is connected to a network
                    leaveButton.setEnabled(true);
                    LowpanProvisioningParams provisioningParams =
                            mLowpanInterface.getLowpanProvisioningParams(false);
                    if (provisioningParams != null) {
                        networkNameView.setText(provisioningParams.getLowpanIdentity().getName());
                    }
                } else {
                    // The LoWPAN interface is not connected to a network
                    leaveButton.setEnabled(false);
                    networkNameView.setText("");
                }

            } else {
                // Display that the LoWPAN interface is not enabled
                enabledView.setChecked(false);
                scanButton.setEnabled(false);
                leaveButton.setEnabled(false);
                sendButton.setEnabled(false);
                networkNameView.setText("");
            }

        } else {
            // Display that there is no LoWPAN interfaces found
            ifaceName.setText(R.string.error_no_lowpans);
            stateView.setText("");
            roleView.setText("");
            networkNameView.setText("");
            enabledView.setChecked(false);
            enabledView.setEnabled(false);
            scanButton.setEnabled(false);
            leaveButton.setEnabled(false);
            sendButton.setEnabled(false);
        }
    }

    void onLowpanInterfaceAdded(LowpanInterface lowpanInterface) {
        // A LoWPAN interface was added
        if (mLowpanInterface == null && lowpanInterface != null) {
            mLowpanInterface = lowpanInterface;
            mLowpanInterface.registerCallback(mLowpanInterfaceCallback);
            // Update the UI
            refreshValues();
        }
    }

    void onLowpanInterfaceRemoved(LowpanInterface lowpanInterface) {
        // A LoWPAN interface was removed
        if (mLowpanInterface == lowpanInterface) {
            mLowpanInterface.unregisterCallback(mLowpanInterfaceCallback);
            mLowpanInterface = null;
            // Update the UI
            refreshValues();
        }
    }

    private class DemoLowpanManagerCallback extends LowpanManager.Callback {
        @Override
        public void onInterfaceAdded(LowpanInterface lowpanInterface) {
            onLowpanInterfaceAdded(lowpanInterface);
        }

        @Override
        public void onInterfaceRemoved(LowpanInterface lowpanInterface) {
            onLowpanInterfaceRemoved(lowpanInterface);
        }
    }

    private class DemoLowpanInterfaceCallback extends LowpanInterface.Callback {
        @Override
        public void onStateChanged(int i) {
            refreshValues();
        }

        @Override
        public void onLowpanIdentityChanged(LowpanIdentity lowpanIdentity) {
            refreshValues();
        }
    }
}
