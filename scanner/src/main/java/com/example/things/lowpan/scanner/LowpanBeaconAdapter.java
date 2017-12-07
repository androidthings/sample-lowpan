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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.things.lowpan.LowpanBeaconInfo;

/**
 * List adapter to bind and display details of a LoWPAN network beacon.
 */
public class LowpanBeaconAdapter extends ArrayAdapter<LowpanBeaconInfo> {

    public LowpanBeaconAdapter(Context context) {
        super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.list_item_beacon, parent, false);
        }

        LowpanBeaconInfo beacon = getItem(position);

        TextView networkNameView = convertView.findViewById(R.id.networkName);
        networkNameView.setText(beacon.getLowpanIdentity().getName());

        TextView xpanidView = convertView.findViewById(R.id.xpanid);
        xpanidView.setText(Utils.bytesToHex(beacon.getLowpanIdentity().getXpanid()));

        TextView chanView = convertView.findViewById(R.id.chanValue);
        chanView.setText(String.format("%d", beacon.getLowpanIdentity().getChannel()));

        TextView panidView = convertView.findViewById(R.id.panidValue);
        panidView.setText(String.format("%04X", beacon.getLowpanIdentity().getPanid()));

        TextView macAddrView = convertView.findViewById(R.id.macaddr);
        macAddrView.setText(Utils.bytesToAddrHex(beacon.getBeaconAddress()));

        ProgressBar rssiProgressView = convertView.findViewById(R.id.rssiProgress);
        int rssi = Utils.rssiToLqi(beacon.getRssi());
        rssiProgressView.setProgress(rssi);

        ProgressBar lqiProgressView = convertView.findViewById(R.id.lqiProgress);
        int lqi = beacon.getLqi();
        lqiProgressView.setProgress(lqi);

        return convertView;
    }
}
