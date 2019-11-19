/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.network;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.Utils;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.storage.FileUtil;
import bisq.common.util.Utilities;

import org.berndpruenster.netlayer.tor.HiddenServiceSocket;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.berndpruenster.netlayer.tor.TorSocket;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.security.SecureRandom;

import java.text.SimpleDateFormat;

import java.net.Socket;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

// Run in UserThread
public class TorNetworkNode extends NetworkNode {

    private static final Logger log = LoggerFactory.getLogger(TorNetworkNode.class);

    private static final int MAX_RESTART_ATTEMPTS = 5;
    private static final long SHUT_DOWN_TIMEOUT = 5;


    private HiddenServiceSocket hiddenServiceSocket;
    private Timer shutDownTimeoutTimer;
    private int restartCounter;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> allShutDown;

    private TorMode torMode;

    private boolean streamIsolation;

    private Socks5Proxy socksProxy;

    private Map<NodeAddress, File> nodeAddressToHSDirectory = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TorNetworkNode(int servicePort, NetworkProtoResolver networkProtoResolver, boolean useStreamIsolation,
            TorMode torMode) {
        super(servicePort, networkProtoResolver);
        this.torMode = torMode;
        this.streamIsolation = useStreamIsolation;
        createExecutorService();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * only prepares a fresh hidden service folder. The actual HS is only established and
     * started on Bisq restart!
     * @return
     */
    @Override
    public File renewHiddenService() {
        // find suitable folder name
        int seed = 0;
        File newDir = null;
        do {
            String newHiddenServiceDirectory = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + seed;
            newDir = new File(torMode.getHiddenServiceBaseDirectory(), newHiddenServiceDirectory);
            seed += 1;
        } while (newDir.exists());

        newDir.mkdirs();

        return newDir;
    }

    /**
     * only marks the folders of the hidden services that are not to be retained for deletion.
     * Once the application shuts down, the folders are deleted so that on app restart,
     * the unnecessary hidden services are gone.
     */
    @Override
    public void clearHiddenServices(Set<NodeAddress> retain) {
        // first and foremost, we always retain the newest HS.
        retain.add(nodeAddressProperty.getValue());

        // then, we clean the hidden service directory accordingly
        // so they are gone after an app restart
        nodeAddressToHSDirectory.entrySet().stream().filter(nodeAddressFileEntry -> !retain.contains(nodeAddressFileEntry.getKey()))
                .forEach(nodeAddressFileEntry -> {
                    deleteHiddenServiceDir(nodeAddressFileEntry.getValue());
                });
    }

    private void deleteHiddenServiceDir(File dir) {
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.error("Error while trying to delete deprecated hidden service directory", e);
        }
    }

    @Override
    public void exportHiddenService(File target) {
        // is directory?
        if (target.isDirectory())
            target = new File(target, nodeAddressProperty.getValue().getHostName());

        if (!target.getName().endsWith(".bisq"))
            target = new File(target.getAbsolutePath() + ".bisq");

        // create zip
        File hiddenServiceDir = nodeAddressToHSDirectory.get(nodeAddressProperty.getValue());
        // write to file
        try {
            FileOutputStream fos = new FileOutputStream(target);
            ZipOutputStream zipOut = new ZipOutputStream(fos);

            Arrays.stream(hiddenServiceDir.listFiles()).filter(file -> !file.isDirectory())
                    .forEach(file -> {
                                try {
                                    zipOut.putNextEntry(new ZipEntry(file.getName()));

                                    FileInputStream fis = new FileInputStream(file);
                                    byte[] bytes = new byte[1024];
                                    int length;
                                    while ((length = fis.read(bytes)) >= 0) {
                                        zipOut.write(bytes, 0, length);
                                    }
                                    fis.close();
                                } catch (IOException e) {
                                    log.error("Error while exporting hidden service.", e);
                                }
                            }
                    );
            zipOut.close();
            fos.close();
        } catch (IOException | NullPointerException e) {
            log.error("Error while exporting hidden service.", e);
        }
    }

    @Override
    public void importHiddenService(File source) throws IOException {
        if (!source.getName().endsWith(".bisq")) {
            log.error("Tried to import from a file not ending in '.bisq'");
            throw new IOException("Cannot read backup file.");
        }

        try {
            // create hidden service directory
            File newHiddenServiceDir = renewHiddenService();

            // unzip contents of source
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(new FileInputStream(source));
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File destination = new File(newHiddenServiceDir, zipEntry.getName());
                FileOutputStream fos = new FileOutputStream(destination);
                int len;
                while ((len = zis.read(buffer)) > 0)
                    fos.write(buffer, 0, len);
                fos.close();
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException e) {
            log.error("Importing a hidden service failed. ", e);
            throw e;
        }
    }

    @Override
    public Set<NodeAddress> getPassiveNodeAddresses() {
        return nodeAddressToHSDirectory.keySet();
    }

    @Override
    public void start(@Nullable SetupListener setupListener) {
        if (setupListener != null)
            addSetupListener(setupListener);

        ListenableFuture<Void> future = executorService.submit(() -> {
            // Create the tor node (takes about 6 sec.)
            createTor(torMode);

            // check if we start our client for the first time
            if (!torMode.getHiddenServiceBaseDirectory().exists())
                renewHiddenService();

            // see if we have to migrate the old file structure
            if (torMode.getHiddenServiceBaseDirectory().listFiles((dir, name) -> name.equals("hostname")).length > 0) {
                File newHiddenServiceDirectory = renewHiddenService();
                for (File current : torMode.getHiddenServiceBaseDirectory().listFiles())
                    current.renameTo(new File(newHiddenServiceDirectory, current.getName()));
            }

            // find hidden service candidates
            File[] hiddenServiceDirs = torMode.getHiddenServiceBaseDirectory().listFiles((dir, name) -> name.matches("\\d{15,}"));

            // start
            CountDownLatch gate = new CountDownLatch(hiddenServiceDirs.length);
            nodeAddressToHSDirectory.clear();

            // sort newest first, so we can just mark duplicate services for deletion
            Arrays.stream(hiddenServiceDirs).sorted(Comparator.comparing(File::getName).reversed())
                    .forEachOrdered(current -> {
                        try {
                            NodeAddress nodeAddress = createHiddenService(current.getName(), Utils.findFreeSystemPort(), servicePort, gate);

                            // use newest HS as for NodeAddress
                            if (nodeAddressProperty.get() == null)
                                nodeAddressProperty.set(nodeAddress);

                            FileUtil.rollingBackup(current, "private_key", 20);

                            nodeAddressToHSDirectory.put(nodeAddress, current);
                        } catch (Exception e) {
                            if (e instanceof IOException && e.getMessage().contains("collision")) {
                                deleteHiddenServiceDir(current);
                                gate.countDown();
                            } else
                                throw e;
                        }
                    });

            UserThread.execute(() -> setupListeners.forEach(SetupListener::onTorNodeReady));

            // only report HiddenServicePublished once all are published
            if (!gate.await(90, TimeUnit.SECONDS)) {
                log.error("{} hidden services failed to start in time.", gate.getCount());
                String msg = "Some hidden services failed to start in time.";
                UserThread.execute(() -> setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(msg))));
            }
            UserThread.execute(() -> setupListeners.forEach(SetupListener::onHiddenServicePublished));

            return null;
        });
        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void ignore) {
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> log.error("Hidden service creation failed: " + throwable));
            }
        });
    }

    @Override
    protected Socket createSocket(NodeAddress peerNodeAddress) throws IOException {
        checkArgument(peerNodeAddress.getHostName().endsWith(".onion"), "PeerAddress is not an onion address");
        // If streamId is null stream isolation gets deactivated.
        // Hidden services use stream isolation by default so we pass null.
        return new TorSocket(peerNodeAddress.getHostName(), peerNodeAddress.getPort(), null);
    }

    // TODO handle failure more cleanly
    public Socks5Proxy getSocksProxy() {
        try {
            String stream = null;
            if (streamIsolation) {
                // create a random string
                byte[] bytes = new byte[512]; // note that getProxy does Sha256 that string anyways
                new SecureRandom().nextBytes(bytes);
                stream = Base64.getEncoder().encodeToString(bytes);
            }

            if (socksProxy == null || streamIsolation) {
                Tor tor = Tor.getDefault();

                // ask for the connection
                socksProxy = tor != null ? tor.getProxy(stream) : null;
            }
            return socksProxy;
        } catch (TorCtlException e) {
            log.error("TorCtlException at getSocksProxy: " + e.toString());
            e.printStackTrace();
            return null;
        } catch (Throwable t) {
            log.error("Error at getSocksProxy: " + t.toString());
            return null;
        }
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        BooleanProperty torNetworkNodeShutDown = torNetworkNodeShutDown();
        BooleanProperty networkNodeShutDown = networkNodeShutDown();
        BooleanProperty shutDownTimerTriggered = shutDownTimerTriggered();

        // Need to store allShutDown to not get garbage collected
        allShutDown = EasyBind.combine(torNetworkNodeShutDown, networkNodeShutDown, shutDownTimerTriggered, (a, b, c) -> (a && b) || c);
        allShutDown.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                shutDownTimeoutTimer.stop();
                long ts = System.currentTimeMillis();
                log.debug("Shutdown executorService");
                try {
                    MoreExecutors.shutdownAndAwaitTermination(executorService, 500, TimeUnit.MILLISECONDS);
                    log.debug("Shutdown executorService done after " + (System.currentTimeMillis() - ts) + " ms.");
                    log.debug("Shutdown completed");
                } catch (Throwable t) {
                    log.error("Shutdown executorService failed with exception: " + t.getMessage());
                    t.printStackTrace();
                } finally {
                    try {
                        if (shutDownCompleteHandler != null)
                            shutDownCompleteHandler.run();
                    } catch (Throwable ignore) {
                    }
                }
            }
        });
    }

    private BooleanProperty torNetworkNodeShutDown() {
        final BooleanProperty done = new SimpleBooleanProperty();
        if (executorService != null) {
            executorService.submit(() -> {
                Utilities.setThreadName("torNetworkNodeShutDown");
                long ts = System.currentTimeMillis();
                log.debug("Shutdown torNetworkNode");
                try {
                    if (Tor.getDefault() != null)
                        Tor.getDefault().shutdown();
                    log.debug("Shutdown torNetworkNode done after " + (System.currentTimeMillis() - ts) + " ms.");
                } catch (Throwable e) {
                    log.error("Shutdown torNetworkNode failed with exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    UserThread.execute(() -> done.set(true));
                }
            });
        } else {
            done.set(true);
        }
        return done;
    }

    private BooleanProperty networkNodeShutDown() {
        final BooleanProperty done = new SimpleBooleanProperty();
        super.shutDown(() -> done.set(true));
        return done;
    }

    private BooleanProperty shutDownTimerTriggered() {
        final BooleanProperty done = new SimpleBooleanProperty();
        shutDownTimeoutTimer = UserThread.runAfter(() -> {
            log.error("A timeout occurred at shutDown");
            done.set(true);
        }, SHUT_DOWN_TIMEOUT);
        return done;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // shutdown, restart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void restartTor(String errorMessage) {
        log.info("Restarting Tor");
        restartCounter++;
        if (restartCounter <= MAX_RESTART_ATTEMPTS) {
            UserThread.execute(() -> {
                setupListeners.forEach(SetupListener::onRequestCustomBridges);
            });
            log.warn("We stop tor as starting tor with the default bridges failed. We request user to add custom bridges.");
            shutDown(null);
        } else {
            String msg = "We tried to restart Tor " + restartCounter +
                    " times, but it continued to fail with error message:\n" +
                    errorMessage + "\n\n" +
                    "Please check your internet connection and firewall and try to start again.";
            log.error(msg);
            throw new RuntimeException(msg);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // create tor
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Attempt to create tor. Handles all exceptions and tries to restart Tor if necessary.
     *
     * @param torMode
     */
    private void createTor(TorMode torMode) {
        try {
            Tor.setDefault(torMode.getTor());
        } catch (TorCtlException e) {
            String msg = e.getCause() != null ? e.getCause().toString() : e.toString();
            log.error("Tor node creation failed: {}", msg);
            if (e.getCause() instanceof IOException) {
                // Since we cannot connect to Tor, we cannot do nothing.
                // Furthermore, we have no hidden services started yet, so there is no graceful
                // shutdown needed either
                UserThread.execute(() -> setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(msg))));
            } else {
                restartTor(e.getMessage());
            }
        } catch (IOException e) {
            log.error("Could not connect to running Tor: {}", e.getMessage());
            // Since we cannot connect to Tor, we cannot do nothing.
            // Furthermore, we have no hidden services started yet, so there is no graceful
            // shutdown needed either
            UserThread.execute(() -> setupListeners.forEach(s -> s.onSetupFailed(new RuntimeException(e.getMessage()))));
        }
    }

    private NodeAddress createHiddenService(String hiddenServiceDirectory, int localPort, int servicePort, CountDownLatch onHSReady) {
        long ts2 = new Date().getTime();
        hiddenServiceSocket = new HiddenServiceSocket(localPort, hiddenServiceDirectory, servicePort);
        NodeAddress nodeAddress = new NodeAddress(hiddenServiceSocket.getServiceName() + ":" + hiddenServiceSocket.getHiddenServicePort());
        hiddenServiceSocket.addReadyListener(socket -> {
            try {
                log.info("\n################################################################\n" +
                                "Tor hidden service published after {} ms. Socked={}\n" +
                                "################################################################",
                        (new Date().getTime() - ts2), socket); //takes usually 30-40 sec
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            startServer(socket);
                        } catch (final Exception e1) {
                            log.error(e1.toString());
                            e1.printStackTrace();
                        }
                    }
                }.start();
            } catch (final Exception e) {
                log.error(e.toString());
                e.printStackTrace();
            }
            return null;
        });
        hiddenServiceSocket.addReadyListener(hiddenServiceSocket1 -> {
            onHSReady.countDown();
            return null;
        });
        log.info("It will take some time for the HS to be reachable (~40 seconds). You will be notified about this");
        return nodeAddress;
    }
}
