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
import net.freehaven.tor.control.ConfigEntry;
import net.freehaven.tor.control.TorControlConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This is where all the fun is, this is the class that handles the heavy work.
 * Note that you will most likely need to actually call into the
 * AndroidOnionProxyManager or JavaOnionProxyManager in order to create the
 * right bindings for your environment.
 * <p/>
 * This class is thread safe but that's mostly because we hit everything over
 * the head with 'synchronized'. Given the way this class is used there
 * shouldn't be any performance implications of this.
 * <p/>
 * This class began life as TorPlugin from the Briar Project
 */
public abstract class OnionProxyManager {
    private static final String[] EVENTS = {"CIRC", "WARN", "ERR"};
    private static final String[] EVENTS_HS = {"EXTENDED", "CIRC", "ORCONN", "INFO", "NOTICE", "WARN", "ERR", "HS_DESC"};

    private static final String OWNER = "__OwningControllerProcess";
    private static final long COOKIE_TIMEOUT_IN_SEC = 10;
    private static final long HOSTNAME_TIMEOUT_IN_SEC = 30;
    private static final Logger log = LoggerFactory.getLogger(OnionProxyManager.class);

    protected final OnionProxyContext onionProxyContext;

    public OnionProxyContext getOnionProxyContext() {
        return onionProxyContext;
    }

    private volatile Socket controlSocket = null;

    // If controlConnection is not null then this means that a connection exists
    // and the Tor OP will die when
    // the connection fails.
    private volatile TorControlConnection controlConnection = null;
    private volatile int control_port;

    private OnionProxyManagerEventHandler eventHandler;

    public OnionProxyManager(OnionProxyContext onionProxyContext) {
        this.onionProxyContext = onionProxyContext;
        eventHandler = new OnionProxyManagerEventHandler();
    }

    public void attachHiddenServiceReadyListener(HiddenServiceDescriptor hs, HiddenServiceReadyListener listener) {
        eventHandler.setHStoWatchFor(hs, listener);
    }

    /**
     * This is a blocking call that will try to start the Tor OP, connect it to
     * the network and get it to be fully bootstrapped. Sometimes the bootstrap
     * process just hangs for no apparent reason so the method will wait for the
     * given time for bootstrap to finish and if it doesn't then will restart
     * the bootstrap process the given number of repeats.
     *
     * @param secondsBeforeTimeOut Seconds to wait for boot strapping to finish
     * @param numberOfRetries      Number of times to try recycling the Tor OP before giving up
     *                             on bootstrapping working
     * @return True if bootstrap succeeded, false if there is a problem or the
     * bootstrap couldn't complete in the given time.
     * @throws java.lang.InterruptedException - You know, if we are interrupted
     * @throws java.io.IOException            - IO Exceptions
     */
    public synchronized boolean startWithRepeat(int secondsBeforeTimeOut, int numberOfRetries)
            throws InterruptedException, IOException {

        if (secondsBeforeTimeOut <= 0 || numberOfRetries < 0) {
            throw new IllegalArgumentException("secondsBeforeTimeOut >= 0 & numberOfRetries > 0");
        }

        try {
            for (int retryCount = 0; retryCount < numberOfRetries; ++retryCount) {
                if (installAndStartTorOp() == false) {
                    return false;
                }
                enableNetwork(true);

                // We will check every second to see if boot strapping has
                // finally finished
                for (int secondsWaited = 0; secondsWaited < secondsBeforeTimeOut; ++secondsWaited) {
                    if (isBootstrapped() == false) {
                        Thread.sleep(1000, 0);
                    } else {
                        log.debug("Tor has bootstrapped");
                        return true;
                    }
                }

                // Bootstrapping isn't over so we need to restart and try again
                stop();

                // Experimentally we have found that if a Tor OP has run before and thus
                // has cached descriptors
                // and that when we try to start it again it won't start then deleting
                // the cached data can fix this.
                // But, if there is cached data and things do work then the Tor OP will
                // start faster than it would
                // if we delete everything.
                // So our compromise is that we try to start the Tor OP 'as is' on the
                // first round and after that
                // we delete all the files.
                onionProxyContext.deleteAllFilesButHiddenServices();
            }

            return false;
        } finally {
            // Make sure we return the Tor OP in some kind of consistent state,
            // even if it's 'off'.
            if (isRunning() == false) {
                stop();
            }
        }
    }

    /**
     * Returns the socks port on the IPv4 localhost address that the Tor OP is
     * listening on
     *
     * @return Discovered socks port
     * @throws java.io.IOException - File errors
     */
    public synchronized int getIPv4LocalHostSocksPort() throws IOException {
        if (isRunning() == false) {
            throw new RuntimeException("Tor is not running!");
        }

        // This returns a set of space delimited quoted strings which could be
        // Ipv4, Ipv6 or unix sockets
        String[] socksIpPorts = controlConnection.getInfo("net/listeners/socks").split(" ");

        for (String address : socksIpPorts) {
            if (address.contains("\"127.0.0.1:")) {
                // Remember, the last character will be a " so we have to remove
                // that
                return Integer.parseInt(address.substring(address.lastIndexOf(":") + 1, address.length() - 1));
            }
        }

        throw new RuntimeException("We don't have an Ipv4 localhost binding for socks!");
    }

    /**
     * Publishes a hidden service
     *
     * @param hiddenServicePort The port that the hidden service will accept connections on
     * @param localPort         The local port that the hidden service will relay connections
     *                          to
     * @return The hidden service's onion address in the form X.onion.
     * @throws java.io.IOException - File errors
     */
    public synchronized String publishHiddenService(int hiddenServicePort, int localPort) throws IOException {
        if (controlConnection == null) {
            throw new RuntimeException("Service is not running.");
        }

        List<ConfigEntry> currentHiddenServices = controlConnection.getConf("HiddenServiceOptions");

        if ((currentHiddenServices.size() == 1
                && currentHiddenServices.get(0).key.compareTo("HiddenServiceOptions") == 0
                && currentHiddenServices.get(0).value.compareTo("") == 0) == false) {
            throw new RuntimeException("Sorry, only one hidden service to a customer and we already have one. Please "
                    + "send complaints to https://github"
                    + ".com/thaliproject/Tor_Onion_Proxy_Library/issues/5 with your scenario so we can justify fixing "
                    + "this.");
        }

        log.debug("Creating hidden service");
        File hostnameFile = onionProxyContext.getHostNameFile();

        if (hostnameFile.getParentFile().exists() == false && hostnameFile.getParentFile().mkdirs() == false) {
            throw new RuntimeException("Could not create hostnameFile parent directory");
        }

        if (hostnameFile.exists() == false && hostnameFile.createNewFile() == false) {
            throw new RuntimeException("Could not create hostnameFile");
        }
        // Thanks, Ubuntu!
        try {
            switch (OsData.getOsType()) {
                case Linux32:
                case Linux64:
                case Mac: {
                    Set<PosixFilePermission> perms = new HashSet<>();
                    perms.add(PosixFilePermission.OWNER_READ);
                    perms.add(PosixFilePermission.OWNER_WRITE);
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(onionProxyContext.getHiddenServiceDirectory().toPath(), perms);
                }
                default:
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        controlConnection.setEvents(Arrays.asList(EVENTS_HS));
        // Watch for the hostname file being created/updated
        WriteObserver hostNameFileObserver = onionProxyContext.generateWriteObserver(hostnameFile);
        // Use the control connection to update the Tor config
        List<String> config = Arrays.asList("HiddenServiceDir " + hostnameFile.getParentFile().getAbsolutePath(),
                "HiddenServicePort " + hiddenServicePort + " 127.0.0.1:" + localPort);
        controlConnection.setConf(config);
        controlConnection.saveConf();
        // Wait for the hostname file to be created/updated
        if (!hostNameFileObserver.poll(HOSTNAME_TIMEOUT_IN_SEC, SECONDS)) {
            FileUtilities.listFilesToLog(hostnameFile.getParentFile());
            throw new RuntimeException("Wait for hidden service hostname file to be created expired.");
        }

        // Publish the hidden service's onion hostname in transport properties
        String hostname = new String(FileUtilities.read(hostnameFile), "UTF-8").trim();
        log.debug("Hidden service config has completed.");

        return hostname;
    }

    public synchronized boolean isHiddenServiceAvailable(String onionurl) {
        try {
            return controlConnection.isHSAvailable(onionurl.substring(0, onionurl.indexOf(".")));
        } catch (IOException e) {
            // We'll have to wait for tor 0.2.7
            e.printStackTrace();
            System.err.println("We'll have to wait for Tor 0.2.7 for HSFETCH to work!");
        }
        return false;
    }

    /**
     * Kills the Tor OP Process. Once you have called this method nothing is
     * going to work until you either call startWithRepeat or
     * installAndStartTorOp
     *
     * @throws java.io.IOException - File errors
     */
    public synchronized void stop() throws IOException {
        try {
            if (controlConnection == null) {
                return;
            }
            log.debug("Stopping Tor");
            controlConnection.setConf("DisableNetwork", "1");
            controlConnection.shutdownTor("TERM");
        } finally {
            if (controlSocket != null) {
                controlSocket.close();
            }
            controlConnection = null;
            controlSocket = null;
        }
    }

    /**
     * Checks to see if the Tor OP is running (e.g. fully bootstrapped) and open
     * to network connections.
     *
     * @return True if running
     * @throws java.io.IOException - IO exceptions
     */
    public synchronized boolean isRunning() throws IOException {
        return isBootstrapped() && isNetworkEnabled();
    }

    /**
     * Tells the Tor OP if it should accept network connections
     *
     * @param enable If true then the Tor OP will accept SOCKS connections,
     *               otherwise not.
     * @throws java.io.IOException - IO exceptions
     */
    public synchronized void enableNetwork(boolean enable) throws IOException {
        if (controlConnection == null) {
            throw new RuntimeException("Tor is not running!");
        }
        log.debug("Enabling network: " + enable);
        controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
    }

    /**
     * Specifies if Tor OP is accepting network connections
     *
     * @return True if network is enabled (that doesn't mean that the device is
     * online, only that the Tor OP is trying to connect to the network)
     * @throws java.io.IOException - IO exceptions
     */
    public synchronized boolean isNetworkEnabled() throws IOException {
        if (controlConnection == null) {
            throw new RuntimeException("Tor is not running!");
        }

        List<ConfigEntry> disableNetworkSettingValues = controlConnection.getConf("DisableNetwork");
        boolean result = false;
        // It's theoretically possible for us to get multiple values back, if
        // even one is false then we will
        // assume all are false
        for (ConfigEntry configEntry : disableNetworkSettingValues) {
            if (configEntry.value.equals("1")) {
                return false;
            } else {
                result = true;
            }
        }
        return result;
    }

    /**
     * Determines if the boot strap process has completed.
     *
     * @return True if complete
     */
    public synchronized boolean isBootstrapped() {
        if (controlConnection == null) {
            return false;
        }

        String phase = null;
        try {
            phase = controlConnection.getInfo("status/bootstrap-phase");
        } catch (IOException e) {
            log.warn("Control connection is not responding properly to getInfo", e);
        }

        if (phase != null && phase.contains("PROGRESS=100")) {
            return true;
        }

        return false;
    }

    /**
     * Installs all necessary files and starts the Tor OP in offline mode (e.g.
     * networkEnabled(false)). This would only be used if you wanted to start
     * the Tor OP so that the install and related is all done but aren't ready
     * to actually connect it to the network.
     *
     * @return True if all files installed and Tor OP successfully started
     * @throws java.io.IOException            - IO Exceptions
     * @throws java.lang.InterruptedException - If we are, well, interrupted
     */
    public synchronized boolean installAndStartTorOp() throws IOException, InterruptedException {
        // The Tor OP will die if it looses the connection to its socket so if
        // there is no controlSocket defined
        // then Tor is dead. This assumes, of course, that takeOwnership works
        // and we can't end up with Zombies.
        if (controlConnection != null) {
            log.debug("Tor is already running");
            return true;
        }

        // The code below is why this method is synchronized, we don't want two
        // instances of it running at once
        // as the result would be a mess of screwed up files and connections.
        log.debug("Tor is not running");

        installAndConfigureFiles();

        log.debug("Starting Tor");
        File cookieFile = onionProxyContext.getCookieFile();
        if (cookieFile.getParentFile().exists() == false && cookieFile.getParentFile().mkdirs() == false) {
            throw new RuntimeException("Could not create cookieFile parent directory");
        }

        // The original code from Briar watches individual files, not a
        // directory and Android's file observer
        // won't work on files that don't exist. Rather than take 5 seconds to
        // rewrite Briar's code I instead
        // just make sure the file exists
        if (cookieFile.exists() == false && cookieFile.createNewFile() == false) {
            throw new RuntimeException("Could not create cookieFile");
        }

        File workingDirectory = onionProxyContext.getWorkingDirectory();
        // Watch for the auth cookie file being created/updated
        WriteObserver cookieObserver = onionProxyContext.generateWriteObserver(cookieFile);
        // Start a new Tor process
        String torPath = onionProxyContext.getTorExecutableFile().getAbsolutePath();
        String configPath = onionProxyContext.getTorrcFile().getAbsolutePath();
        String pid = onionProxyContext.getProcessId();
        String[] cmd = {torPath, "-f", configPath, OWNER, pid};
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        onionProxyContext.setEnvironmentArgsAndWorkingDirectoryForStart(processBuilder);
        Process torProcess = null;
        try {
            // torProcess = Runtime.getRuntime().exec(cmd, env,
            // workingDirectory);
            torProcess = processBuilder.start();
            CountDownLatch controlPortCountDownLatch = new CountDownLatch(1);
            eatStream(torProcess.getInputStream(), false, controlPortCountDownLatch);
            eatStream(torProcess.getErrorStream(), true, null);

            // On platforms other than Windows we run as a daemon and so we need
            // to wait for the process to detach
            // or exit. In the case of Windows the equivalent is running as a
            // service and unfortunately that requires
            // managing the service, such as turning it off or uninstalling it
            // when it's time to move on. Any number
            // of errors can prevent us from doing the cleanup and so we would
            // leave the process running around. Rather
            // than do that on Windows we just let the process run on the exec
            // and hence don't look for an exit code.
            // This does create a condition where the process has exited due to
            // a problem but we should hopefully
            // detect that when we try to use the control connection.
            if (OsData.getOsType() != OsData.OsType.Windows) {
                int exit = torProcess.waitFor();
                torProcess = null;
                if (exit != 0) {
                    log.warn("Tor exited with value " + exit);
                    return false;
                }
            }

            // Wait for the auth cookie file to be created/updated
            if (!cookieObserver.poll(COOKIE_TIMEOUT_IN_SEC, SECONDS)) {
                log.warn("Auth cookie not created");
                FileUtilities.listFilesToLog(workingDirectory);
                return false;
            }

            // Now we should be able to connect to the new process
            controlPortCountDownLatch.await();
            controlSocket = new Socket("127.0.0.1", control_port);

            // Open a control connection and authenticate using the cookie file
            TorControlConnection controlConnection = new TorControlConnection(controlSocket);
            controlConnection.authenticate(FileUtilities.read(cookieFile));
            // Tell Tor to exit when the control connection is closed
            controlConnection.takeOwnership();
            controlConnection.resetConf(Collections.singletonList(OWNER));

            controlConnection.setEventHandler(eventHandler);
            controlConnection.setEvents(Arrays.asList(EVENTS));

            // We only set the class property once the connection is in a known
            // good state
            this.controlConnection = controlConnection;
            return true;
        } catch (SecurityException e) {
            log.warn(e.toString(), e);
            return false;
        } catch (InterruptedException e) {
            log.warn("Interrupted while starting Tor", e);
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (controlConnection == null && torProcess != null) {
                // It's possible that something 'bad' could happen after we
                // executed exec but before we takeOwnership()
                // in which case the Tor OP will hang out as a zombie until this
                // process is killed. This is problematic
                // when we want to do things like
                torProcess.destroy();
            }
        }
    }

    /**
     * Returns the root directory in which the Tor Onion Proxy keeps its files.
     * This is mostly intended for debugging purposes.
     *
     * @return Working directory for Tor Onion Proxy files
     */
    public File getWorkingDirectory() {
        return onionProxyContext.getWorkingDirectory();
    }

    protected void eatStream(final InputStream inputStream, final boolean stdError,
                             final CountDownLatch countDownLatch) {
        new Thread("eatStream") {
            @Override
            public void run() {
                Thread.currentThread().setName(stdError ? "eatInputStream" : "eatErrorStream");
                Scanner scanner = new Scanner(inputStream);
                try {
                    while (scanner.hasNextLine()) {
                        if (stdError) {
                            log.error(scanner.nextLine());
                        } else {
                            String nextLine = scanner.nextLine();
                            // We need to find the line where it tells us what
                            // the control port is.
                            // The line that will appear in stdio with the
                            // control port looks like:
                            // Control listener listening on port 39717.
                            if (nextLine.contains("Control listener listening on port ")) {
                                // For the record, I hate regex so I'm doing
                                // this manually
                                control_port = Integer.parseInt(
                                        nextLine.substring(nextLine.lastIndexOf(" ") + 1, nextLine.length() - 1));
                                countDownLatch.countDown();
                            }
                            log.debug(nextLine);
                        }
                    }
                } finally {
                    scanner.close();
                    try {
                        inputStream.close();

                    } catch (IOException e) {
                        log.error("Couldn't close input stream in eatStream", e);
                    }
                }
            }
        }.start();
    }

    protected synchronized void installAndConfigureFiles() throws IOException, InterruptedException {
        onionProxyContext.installFiles();

        if (!setExecutable(onionProxyContext.getTorExecutableFile())) {
            throw new RuntimeException("could not make Tor executable.");
        }

        // We need to edit the config file to specify exactly where the
        // cookie/geoip files should be stored, on
        // Android this is always a fixed location relative to the configFiles
        // which is why this extra step
        // wasn't needed in Briar's Android code. But in Windows it ends up in
        // the user's AppData/Roaming. Rather
        // than track it down we just tell Tor where to put it.
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(new BufferedWriter(new FileWriter(onionProxyContext.getTorrcFile(), true)));
            printWriter.println("CookieAuthFile " + onionProxyContext.getCookieFile().getAbsolutePath());
            // For some reason the GeoIP's location can only be given as a file
            // name, not a path and it has
            // to be in the data directory so we need to set both
            printWriter.println("DataDirectory " + onionProxyContext.getWorkingDirectory().getAbsolutePath());
            printWriter.println("GeoIPFile " + onionProxyContext.getGeoIpFile().getName());
            printWriter.println("GeoIPv6File " + onionProxyContext.getGeoIpv6File().getName());
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
    }

    /**
     * Alas old versions of Android do not support setExecutable.
     *
     * @param f File to make executable
     * @return True if it worked, otherwise false.
     */
    protected abstract boolean setExecutable(File f);
}
