# IPFS Lite

IPFS Lite is an application to support the standard use cases of IPFS

>This is my personal fork of @remmerw's Android IPFS application

## General 

The basic characteristics of the app are decentralized, respect of personal data,
open source, free of charge, transparent, free of advertising and legally impeccable.

## Documentation

**IPFS Lite** is a decentralized file-sharing application which based on
the following core technologies.
- IPFS (https://ipfs.io/) 
<br>The IPFS technology is used to support the decentralized file sharing use-cases of this application.

### **IPFS Lite** versus **IPFS**

This section describes the differences between an **IPFS Lite** node and a regular **IPFS** node.
<br>In general an **IPFS Lite** has the same functionality like an regular node.
There are some small differences which are described here. The reasons are outlined in brackets.
- **No** Gateway Support
<br>An IPFS gateway is not supported (Performance,Security,Android 10)
- **No** CLI and HTTP API Support
<br>No public API is supported, the application itself based on the internal IPFS Core API (Android 10)
- **No** WebUI Support
<br>The WebUI feature is not supported (Performance,Security,Android 10)

## Privacy Policy

### Data Protection

<p>As an application provider, the protection of all personal data is taken very seriously.
All personal information is treated confidentially and in accordance with the legal requirements,
regulations, as explained in this privacy policy.</p>
<p>This app is designed so that the user do not have to enter any personal data. Never will data
collected by us, and especially not passed to third parties. The users behaviour is also not
analyzed by this application.</p>
<p>The user is responsible what kind of data is added or retrieved from the IPFS network.
This kind of information is also not tracked by this application.</p>

### Android Permissions
<p>This section describes briefly why specific Android permissions are required.</p>
            <ul>
                <li>
                    <h4>Camera</h4>
                    <p>The camera permission is required to read QR codes, which contains
                        information about peer ID's (PIDs) or content data (URLs).
                    </p>
                </li>
                <li>
                    <h4>Foreground Service</h4>
                    <p>The foreground service permission is required to run the IPFS node over a
                        longer period of time.
                    </p>
                </li>
            </ul>
