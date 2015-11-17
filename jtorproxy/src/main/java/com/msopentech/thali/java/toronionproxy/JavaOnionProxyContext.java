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

package com.msopentech.thali.java.toronionproxy;

import com.msopentech.thali.toronionproxy.FileUtilities;
import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.OsData;
import com.msopentech.thali.toronionproxy.WriteObserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class JavaOnionProxyContext extends OnionProxyContext {

    public JavaOnionProxyContext(File workingDirectory) {
        super(workingDirectory);
    }

    @Override
    public WriteObserver generateWriteObserver(File file) {
        try {
            return new JavaWatchObserver(file);
        } catch (IOException e) {
            throw new RuntimeException("Could not create JavaWatchObserver", e);
        }
    }

    @Override
    protected InputStream getAssetOrResourceByName(String fileName) throws IOException {
        return getClass().getResourceAsStream("/" + fileName);
    }

    @Override
    public String getProcessId() {
        // This is a horrible hack. It seems like more JVMs will return the
        // process's PID this way, but not guarantees.
        String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        return processName.split("@")[0];
    }

    @Override
    public void installFiles() throws IOException, InterruptedException {
        super.installFiles();
        switch (OsData.getOsType()) {
            case Windows:
            case Linux32:
            case Linux64:
            case Mac:
                FileUtilities.extractContentFromZip(getWorkingDirectory(),
                        getAssetOrResourceByName(getPathToTorExecutable() + "tor.zip"));
                break;
            default:
                throw new RuntimeException("We don't support Tor on this OS yet");
        }
    }

    @Override
    protected String getPathToTorExecutable() {
        String path = "native/";
        switch (OsData.getOsType()) {
            case Windows:
                return path + "windows/x86/"; // We currently only support the
            // x86 build but that should work
            // everywhere
            case Mac:
                return path + "osx/x64/"; // I don't think there even is a x32
            // build of Tor for Mac, but could be
            // wrong.
            case Linux32:
                return path + "linux/x86/";
            case Linux64:
                return path + "linux/x64/";
            default:
                throw new RuntimeException("We don't support Tor on this OS");
        }
    }

    @Override
    protected String getTorExecutableFileName() {
        switch (OsData.getOsType()) {
            case Linux32:
            case Linux64:
                return "tor";
            case Windows:
                return "tor.exe";
            case Mac:
                return "tor.real";
            default:
                throw new RuntimeException("We don't support Tor on this OS");
        }
    }
}
