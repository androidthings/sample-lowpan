Android Things LoWPAN Sample
============================

This sample shows how to use LoWPAN to connect multiple Android Things
devices in the same network.

Pre-requisites
--------------

- 2 Android Things compatible boards
- Display with touch input or external pointing device
- Android Studio 2.2+
- Rainbow Hat
- LoWPAN peripherals

Setting up LoWPAN Drivers
-------------------------

To interface an Android Things device with LoWPAN, you can write a `UserLowpanDriver`.

#### `UserLowpanDriver`

Read the documentation on how to write a LoWPAN driver for a specific
peripheral.

#### USB Drivers

If you are using a USB device, you can connect it to your Android Things
board and then set the LoWPAN HAL to use the USB port:

```
adb root
adb shell setprop ro.lowpan.hal.device /dev/ttyUSB0
```


Build and install
=================

On Android Studio, click on the "Run" button.

If you prefer to run on the command line, type

```bash
./gradlew installDebug
adb shell am start com.example.things.lowpan/.MainActivity
```

### Hardware Setup

To best use the demo, you should have the following hardware configuration:

* Android Things Sender
    * Should be attached to a touchscreen or a screen and attached pointer device
* Android Things Receiver
    * Should be attached to a touchscreen or a screen and attached pointer device
    * Can optionally be attached to a Rainbow Hat

### How to use
#### Connect to LoWPAN Provisioned Network

First, you must connect your devices to a provisioned network. You can scan
for networks by selecting the **SCAN** button in the MainActivity. It will
find nearby networks and allow you to join one.

#### Identify the address of your receiver device

Follow these steps to setup the app to make local connections:

1. Open a shell to the device you want as the receiver
1. Execute `lowpanctl status`
1. Copy the second address from the output
1. In `DemoActivity.java` replace the `SERVER_ADDRESS` with this value

#### Using the app

After joining a network you should, on the Sending device, press the
 **SENDER** button. You will be presented with a slider. In this screen,
 press the **CONNECT** button. After connecting successfully, you can
 move the slider and send a different value wirelessly to the receiving
 device.

 On the receiving device, you should join the same network then press the
 **RECEIVER** button.

To learn more about LoWPAN networks, read the documentation.

License
-------

Copyright 2017 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
