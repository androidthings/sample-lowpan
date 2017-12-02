# Android Things LoWPAN Sample

This sample shows how to use LoWPAN to connect multiple Android Things
devices in the same network and wirelessly transfer data. There are
three modules in this sample which demonstrate how to scan for networks,
transmit data, and receive data.

## Pre-requisites

- 2 Android Things compatible boards
- Displays or Rainbow Hats
- Android Studio 2.2+
- LoWPAN peripherals

## Integrating LoWPAN Hardware

To interface an Android Things device with LoWPAN, you can write a `LowpanDriver`.

### `LowpanDriver`

Read the documentation on how to write a [LoWPAN driver](https://developer.android.com/things/reference/com/google/android/things/userdriver/LowpanDriver.html) for a specific
peripheral.

### Pre-Built NCP Firmware
To get started with a pre-existing development board, you can download
a firmware image from the [OpenThread](https://openthread.io/guides/ncp/firmware)
website and flash one of the supported boards.

# Build and install

On Android Studio, click on the "Run" button.

If you prefer to run on the command line, type

```bash
./gradlew installDebug
adb shell am start com.example.things.lowpan.scanner/.LowpanScanActivity
```
```bash
./gradlew installDebug
adb shell am start com.example.things.lowpan.transmitter/.TransmitterActivity
```
```bash
./gradlew installDebug
adb shell am start com.example.things.lowpan.scanner/.ReceiverActivity
```

### How to use

#### Scanner
The Scanner module demonstrates how to scan for other networks. It can
view existing networks, join existing networks, or form a new network.

This module requires a screen and input device.

#### Transmitter
The Transmitter module demonstrates how to send data to other devices.
When it is created, it provisions a network called **lowpan_sample**.

Before running this module, you should identify the address of the receiver
device and replace `SERVER_ADDRESS` in `TransmitterActivity`.

If you have a Rainbow Hat or a screen, you can control the module and view
its status. It will display "READY" when the sample is ready to connect.

Pressing the **A** button on the Rainbow Hat will initiate a connection
to the receiver. It will say "CONNECTED" if successful.

Once that happens you can control a seekbar on the screen. Alternatively,
the **B** and **C** buttons on the Rainbow Hat will decrement or increment
the value. The value is then transmitted wirelessly to the receiver device.

#### Receiver
The Receiver module demonstrates how to connect to a network socket and
receive data from other devices. When it starts, it will do a scan of
nearby networks. If it finds the network called **lowpan_sample** it will
try to join.

When it joins, it will update the status to say "CONNECTED". Then, as it
receives data from the transmitter, it will change the value displayed
on the screen as well as on the Rainbow Hat.

#### Identify the address of your receiver device

Follow these steps to setup the app to make local connections:

1. Open a shell to the device you want as the receiver
1. Execute `lowpanctl status`
1. Note the address. This will have the prefix "fe80::".
1. In `DemoActivity.java` replace the `SERVER_ADDRESS` with this value

To learn more about LoWPAN networks, read the [documentation](https://developer.android.com/things/reference/com/google/android/things/lowpan/package-summary.html).

## License

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
