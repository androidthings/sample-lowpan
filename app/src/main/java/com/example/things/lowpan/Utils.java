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

import com.google.android.things.lowpan.LowpanInterface;

public class Utils {
    public static String stateToString(int state) {
        switch (state) {
            case LowpanInterface.STATE_OFFLINE:
                return "offline";
            case LowpanInterface.STATE_ATTACHING:
                return "attaching";
            case LowpanInterface.STATE_ATTACHED:
                return "attached";
            case LowpanInterface.STATE_FAULT:
                return "fault";
            default:
                return Integer.toString(state);
        }
    }

    public static String roleToString(int role) {
        switch (role) {
            case LowpanInterface.ROLE_DETACHED:
                return "detached";
            case LowpanInterface.ROLE_END_DEVICE:
                return "end-device";
            case LowpanInterface.ROLE_ROUTER:
                return "router";
            case LowpanInterface.ROLE_LEADER:
                return "leader";
            default:
                return Integer.toString(role);
        }
    }

    public static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static String bytesToAddrHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < in.length; i++) {
            if (i != 0) {
                builder.append(":");
            }
            builder.append(String.format("%02x", in[i]));
        }
        return builder.toString();
    }

    public static int rssiToLqi(int rssi) {
        /* Quick and dirty LQI from RSSI */
        int lqi;
        final int high_rssi = -45;
        final int low_rssi = -90;
        lqi = (rssi - low_rssi) * 254 / (high_rssi - low_rssi) + 1;

        if (lqi < 1) {
            lqi = 1;
        } else if (lqi > 255) {
            lqi = 255;
        }
        return lqi;
    }
}
