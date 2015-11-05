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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class encapsulates data that is handled differently in Java and Android
 * as well as managing file locations.
 */
abstract public class OnionProxyContext {
    protected final static String hiddenserviceDirectoryName = "hiddenservice";
    protected final static String geoIpName = "geoip";
    protected final static String geoIpv6Name = "geoip6";
    protected final static String torrcName = "torrc";
    protected final File workingDirectory;
    protected final File geoIpFile;
    protected final File geoIpv6File;
    protected final File torrcFile;
    protected final File torExecutableFile;
    protected final File cookieFile;
    protected final File hostnameFile;

    public OnionProxyContext(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        geoIpFile = new File(getWorkingDirectory(), geoIpName);
        geoIpv6File = new File(getWorkingDirectory(), geoIpv6Name);
        torrcFile = new File(getWorkingDirectory(), torrcName);
        torExecutableFile = new File(getWorkingDirectory(), getTorExecutableFileName());
        cookieFile = new File(getWorkingDirectory(), ".tor/control_auth_cookie");
        hostnameFile = new File(getWorkingDirectory(), "/" + hiddenserviceDirectoryName
                + "/hostname");
    }

    public void installFiles() throws IOException, InterruptedException {
        // This is sleezy but we have cases where an old instance of the Tor OP
        // needs an extra second to
        // clean itself up. Without that time we can't do things like delete its
        // binary (which we currently
        // do by default, something we hope to fix with
        // https://github.com/thaliproject/Tor_Onion_Proxy_Library/issues/13
        Thread.sleep(1000, 0);

        try {
            File dotTorDir = new File(getWorkingDirectory(), ".tor");
            if (dotTorDir.exists())
                FileUtilities.recursiveFileDelete(dotTorDir);
        } catch (Exception e) {
        }
        if (workingDirectory.exists() == false && workingDirectory.mkdirs() == false) {
            throw new RuntimeException("Could not create root directory!");
        }

        FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(geoIpName), geoIpFile);
        FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(geoIpv6Name), geoIpv6File);
        FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(torrcName), torrcFile);

        switch (OsData.getOsType()) {
            case Android:
                FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(getPathToTorExecutable()
                        + getTorExecutableFileName()), torExecutableFile);
                break;
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

    /**
     * Sets environment variables and working directory needed for Tor
     *
     * @param processBuilder we will call start on this to run Tor
     */
    public void setEnvironmentArgsAndWorkingDirectoryForStart(ProcessBuilder processBuilder) {
        processBuilder.directory(getWorkingDirectory());
        Map<String, String> environment = processBuilder.environment();
        environment.put("HOME", getWorkingDirectory().getAbsolutePath());
        switch (OsData.getOsType()) {
            case Linux32:
            case Linux64:
                // We have to provide the LD_LIBRARY_PATH because when looking
                // for dynamic libraries
                // Linux apparently will not look in the current directory by
                // default. By setting this
                // environment variable we fix that.
                environment.put("LD_LIBRARY_PATH", getWorkingDirectory().getAbsolutePath());
            default:
                break;
        }
    }

    public String[] getEnvironmentArgsForExec() {
        List<String> envArgs = new ArrayList<String>();
        envArgs.add("HOME=" + getWorkingDirectory().getAbsolutePath());
        switch (OsData.getOsType()) {
            case Linux32:
            case Linux64:
                // We have to provide the LD_LIBRARY_PATH
                envArgs.add("LD_LIBRARY_PATH=" + getWorkingDirectory().getAbsolutePath());
            default:
                break;
        }
        return envArgs.toArray(new String[envArgs.size()]);
    }

    public File getGeoIpFile() {
        return geoIpFile;
    }

    public File getGeoIpv6File() {
        return geoIpv6File;
    }

    public File getTorrcFile() {
        return torrcFile;
    }

    public File getCookieFile() {
        return cookieFile;
    }

    public File getHostNameFile() {
        return hostnameFile;
    }

    public File getTorExecutableFile() {
        return torExecutableFile;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public void deleteAllFilesButHiddenServices() throws InterruptedException {
        // It can take a little bit for the Tor OP to detect the connection is
        // dead and kill itself
        Thread.sleep(1000);
        for (File file : getWorkingDirectory().listFiles()) {
            if (file.isDirectory()) {
                if (file.getName().compareTo(hiddenserviceDirectoryName) != 0) {
                    FileUtilities.recursiveFileDelete(file);
                }
            } else {
                if (file.delete() == false) {
                    throw new RuntimeException("Could not delete file " + file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Files we pull out of the AAR or JAR are typically at the root but for
     * executables outside of Android the executable for a particular platform
     * is in a specific sub-directory.
     *
     * @return Path to executable in JAR Resources
     */
    protected String getPathToTorExecutable() {
        String path = "native/";
        switch (OsData.getOsType()) {
            case Android:
                return "";
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

    protected String getTorExecutableFileName() {
        switch (OsData.getOsType()) {
            case Android:
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

    abstract public String getProcessId();

    abstract public WriteObserver generateWriteObserver(File file);

    abstract protected InputStream getAssetOrResourceByName(String fileName) throws IOException;

    public File getHiddenServiceDirectory() {
        return new File(getWorkingDirectory(), "/" + hiddenserviceDirectoryName);
    }
}
