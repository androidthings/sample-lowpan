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

    private Button mScanButton;
    private Button mSendButton;
    private Button mReceiverButton;
    private Button mLeaveButton;
    private CheckBox mEnabledCheckbox;
    private TextView mInterfaceName;
    private TextView mStateView;
    private TextView mRoleView;
    private TextView mNetworkNameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Setup UI elements
        mScanButton = findViewById(R.id.scanButton);
        mLeaveButton = findViewById(R.id.leaveButton);
        mSendButton = findViewById(R.id.sendButton);
        mReceiverButton = findViewById(R.id.receiverButton);
        mEnabledCheckbox = findViewById(R.id.isEnabled);

        mInterfaceName = findViewById(R.id.ifaceName);
        mStateView = findViewById(R.id.state);
        mRoleView = findViewById(R.id.role);
        mNetworkNameView = findViewById(R.id.networkName);

        // Set events to each UI element when a click occurs
        mScanButton.setOnClickListener((buttonView) -> onScanActivity());
        mSendButton.setOnClickListener((buttonView) -> onSenderActivity());
        mReceiverButton.setOnClickListener((buttonView) -> onReceiverActivity());
        mLeaveButton.setOnClickListener((buttonView) -> onLeave());
        mEnabledCheckbox.setOnCheckedChangeListener(this::onEnabledChanged);

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
            mLowpanManagerCallback.onInterfaceAdded(lowpanInterface);
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

    /**
     * Switches to the scan activity.
     */
    private void onScanActivity() {
        // Start scanning for networks
        Intent intent = new Intent(MainActivity.this, LowpanScanActivity.class);
        startActivity(intent);
    }

    /**
     * Switches to the sender activity.
     */
    private void onSenderActivity() {
        // Start sending data to another device
        Intent intent = new Intent(MainActivity.this, SenderActivity.class);
        startActivity(intent);
    }

    /**
     * Switches to the receiver activity.
     */
    private void onReceiverActivity() {
        // Start receiving data from another device
        Intent intent = new Intent(MainActivity.this, ReceiverActivity.class);
        startActivity(intent);
    }

    /**
     * Tries to leave a currently-connected LoWPAN network.
     */
    private void onLeave() {
        try {
            // Leave the current network
            mLowpanInterface.leave();
        } catch (LowpanException e) {
            Log.e(TAG, "Leaving LoWPAN network failed", e);
        }
        refreshValues();
    }

    /**
     * Allows the LoWPAN network to be enabled or disabled with a Checkbox.
     *
     * @param checkBox The Checkbox widget
     * @param value Whether the box was checked.
     */
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

    /**
     * Change the values displayed in the UI.
     */
    private void refreshValues() {
        if (mLowpanInterface != null) {
            // A LoWPAN interface was found for this device
            mInterfaceName.setText(mLowpanInterface.getName());
            mStateView.setText(Utils.stateToString(mLowpanInterface.getState()));
            mRoleView.setText(Utils.roleToString(mLowpanInterface.getRole()));
            mEnabledCheckbox.setEnabled(true);

            refreshLowpanInterfaceEnabled(mLowpanInterface.isEnabled());
            refreshLowpanIsProvisioned(mLowpanInterface.isProvisioned());

        } else {
            // Display that there is no LoWPAN interfaces found
            mInterfaceName.setText(R.string.error_no_lowpans);
            mStateView.setText("");
            mRoleView.setText("");
            mNetworkNameView.setText("");
            mEnabledCheckbox.setChecked(false);
            mEnabledCheckbox.setEnabled(false);
            mScanButton.setEnabled(false);
            mLeaveButton.setEnabled(false);
            mSendButton.setEnabled(false);
        }
    }

    /**
     * Updates the UI based on the whether LoWPAN interface is enabled.
     *
     * @param isEnabled Whether the LoWPAN interface is enabled.
     */
    private void refreshLowpanInterfaceEnabled(boolean isEnabled) {
        if (isEnabled) {
            // The LoWPAN interface is enabled
            mEnabledCheckbox.setChecked(true);
            mScanButton.setEnabled(true);
            mSendButton.setEnabled(mLowpanInterface.getState() == LowpanInterface.STATE_ATTACHED);
        } else {
            // Display that the LoWPAN interface is not enabled
            mEnabledCheckbox.setChecked(false);
            mScanButton.setEnabled(false);
            mLeaveButton.setEnabled(false);
            mSendButton.setEnabled(false);
            mNetworkNameView.setText("");
        }
    }

    /**
     * Updates the UI based on the whether LoWPAN interface is provisioned.
     *
     * @param isProvisioned Whether the LoWPAN interface is provisioned.
     */
    private void refreshLowpanIsProvisioned(boolean isProvisioned) {
        if (isProvisioned) {
            // The LoWPAN interface is connected to a network
            mLeaveButton.setEnabled(true);
            LowpanProvisioningParams provisioningParams =
                    mLowpanInterface.getLowpanProvisioningParams(false);
            if (provisioningParams != null) {
                mNetworkNameView.setText(provisioningParams.getLowpanIdentity().getName());
            }
        } else {
            // The LoWPAN interface is not connected to a network
            mLeaveButton.setEnabled(false);
            mNetworkNameView.setText("");
        }
    }

    private class DemoLowpanManagerCallback extends LowpanManager.Callback {
        @Override
        public void onInterfaceAdded(LowpanInterface lowpanInterface) {
            // A LoWPAN interface was added
            if (mLowpanInterface == null && lowpanInterface != null) {
                mLowpanInterface = lowpanInterface;
                mLowpanInterface.registerCallback(mLowpanInterfaceCallback);
                // Update the UI
                refreshValues();
            }
        }

        @Override
        public void onInterfaceRemoved(LowpanInterface lowpanInterface) {
            // A LoWPAN interface was removed
            if (mLowpanInterface == lowpanInterface) {
                mLowpanInterface.unregisterCallback(mLowpanInterfaceCallback);
                mLowpanInterface = null;
                // Update the UI
                refreshValues();
            }
        }
    }

    private class DemoLowpanInterfaceCallback extends LowpanInterface.Callback {
        @Override
        public void onStateChanged(int newState) {
            // When there is a change in the LoWPAN interface, update the UI
            refreshValues();
        }

        @Override
        public void onLowpanIdentityChanged(LowpanIdentity lowpanIdentity) {
            // When there is a change in the LoWPAN identity, update the UI
            refreshValues();
        }

        @Override
        public void onProvisionException(Exception e) {
            // This callback will only be run if we try to leave the network and the command fails
            Log.e(TAG, "Unable to leave network", e);
        }
    }
}
