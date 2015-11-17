/*
Copyright (C) 2011-2014 Sublime Software Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

/*
 Copyright (c) Microsoft Open Technologies, Inc.
 All Rights Reserved
 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

 THIS CODE IS PROVIDED ON AN *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 INCLUDING WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A PARTICULAR PURPOSE,
 MERCHANTABLITY OR NON-INFRINGEMENT.

 See the Apache 2 License for the specific language governing permissions and limitations under the License.
 */

package com.msopentech.thali.toronionproxy;

import io.nucleo.net.HiddenServiceDescriptor;
import io.nucleo.net.HiddenServiceReadyListener;
import net.freehaven.tor.control.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

/**
 * Logs the data we get from notifications from the Tor OP. This is really just
 * meant for debugging.
 */
public class OnionProxyManagerEventHandler implements EventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(OnionProxyManagerEventHandler.class);
    private HiddenServiceDescriptor hs;
    private HiddenServiceReadyListener listener;
    private boolean hsPublished;

    public void setHStoWatchFor(HiddenServiceDescriptor hs, HiddenServiceReadyListener listener) {
        if (hs == this.hs && hsPublished) {
            listener.onConnect(hs);
            return;
        }
        this.listener = listener;
        this.hs = hs;
        hsPublished = false;
    }

    @Override
    public void circuitStatus(String status, String id, String path) {
        String msg = "CircuitStatus: " + id + " " + status + ", " + path;
        LOG.debug(msg);
    }

    @Override
    public void streamStatus(String status, String id, String target) {
        final String msg = "streamStatus: status: " + status + ", id: " + id + ", target: " + target;
        LOG.debug(msg);

    }

    @Override
    public void orConnStatus(String status, String orName) {
        final String msg = "OR connection: status: " + status + ", orName: " + orName;
        LOG.debug(msg);
    }

    @Override
    public void bandwidthUsed(long read, long written) {
        LOG.debug("bandwidthUsed: read: " + read + ", written: " + written);

    }

    @Override
    public void newDescriptors(List<String> orList) {
        Iterator<String> iterator = orList.iterator();
        StringBuilder stringBuilder = new StringBuilder();
        while (iterator.hasNext()) {
            stringBuilder.append(iterator.next());
        }
        final String msg = "newDescriptors: " + stringBuilder.toString();
        LOG.debug(msg);

    }

    @Override
    public void message(String severity, String msg) {
        final String msg2 = "message: severity: " + severity + ", msg: " + msg;
        LOG.debug(msg2);
        if (severity.equalsIgnoreCase("INFO"))
            checkforHS(msg);
    }

    @Override
    public void unrecognized(String type, String msg) {
        final String msg2 = "unrecognized: type: " + type + ", msg: " + msg;
        LOG.debug(msg2);
    }

    private void checkforHS(String msg) {
        if (hs == null || hsPublished == true)
            return;
        String pattern = "uploading rendezvous descriptor";
        if (msg.toLowerCase().contains(pattern)) {
            hsPublished = true;
            LOG.info("Hidden service " + hs.getFullAddress() + " published.");
            listener.onConnect(hs);
        }
    }
}
