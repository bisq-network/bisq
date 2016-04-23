package io.bitsquare.p2p.seed;

import com.google.common.annotations.VisibleForTesting;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.Clock;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.peers.PeerManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class SeedNode {
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);

    private NodeAddress mySeedNodeAddress = new NodeAddress("localhost:8001");
    private boolean useLocalhost = false;
    private Set<NodeAddress> progArgSeedNodes;
    private P2PService seedNodeP2PService;
    private boolean stopped;
    private final String defaultUserDataDir;

    public SeedNode(String defaultUserDataDir) {
        Log.traceCall("defaultUserDataDir=" + defaultUserDataDir);
        this.defaultUserDataDir = defaultUserDataDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // args: myAddress (incl. port) bitcoinNetworkId maxConnections useLocalhost seedNodes (separated with |)
    // 2. and 3. args are optional
    // eg. lmvdenjkyvx2ovga.onion:8001 0 20 false eo5ay2lyzrfvx2nr.onion:8002|si3uu56adkyqkldl.onion:8003
    // or when using localhost:  localhost:8001 2 20 true localhost:8002|localhost:8003
    // BitcoinNetworkId: The id for the bitcoin network (Mainnet = 0, TestNet = 1, Regtest = 2)
    public void processArgs(String[] args) {
        try {
            if (args.length > 0) {
                String arg0 = args[0];
                checkArgument(arg0.contains(":") && arg0.split(":").length == 2 && arg0.split(":")[1].length() > 3, "Wrong program argument: " + arg0);
                mySeedNodeAddress = new NodeAddress(arg0);
                log.info("From processArgs: mySeedNodeAddress=" + mySeedNodeAddress);
                if (args.length > 1) {
                    String arg1 = args[1];
                    int networkId = Integer.parseInt(arg1);
                    log.info("From processArgs: networkId=" + networkId);
                    checkArgument(networkId > -1 && networkId < 3,
                            "networkId out of scope (Mainnet = 0, TestNet = 1, Regtest = 2)");
                    Version.setBtcNetworkId(networkId);
                    if (args.length > 2) {
                        String arg2 = args[2];
                        int maxConnections = Integer.parseInt(arg2);
                        log.info("From processArgs: maxConnections=" + maxConnections);
                        checkArgument(maxConnections < 1000, "maxConnections seems to be a bit too high...");
                        PeerManager.setMaxConnections(maxConnections);
                    } else {
                        // we keep default a higher connection size for seed nodes
                        PeerManager.setMaxConnections(50);
                    }
                    if (args.length > 3) {
                        String arg3 = args[3];
                        checkArgument(arg3.equals("true") || arg3.equals("false"));
                        useLocalhost = ("true").equals(arg3);
                        log.info("From processArgs: useLocalhost=" + useLocalhost);
                    }
                    if (args.length > 4) {
                        String arg4 = args[4];
                        checkArgument(arg4.contains(":") && arg4.split(":").length > 1 && arg4.split(":")[1].length() > 3,
                                "Wrong program argument");
                        List<String> list = Arrays.asList(arg4.split("\\|"));
                        progArgSeedNodes = new HashSet<>();
                        list.forEach(e -> {
                            checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4,
                                    "Wrong program argument");
                            progArgSeedNodes.add(new NodeAddress(e));
                        });
                        log.info("From processArgs: progArgSeedNodes=" + progArgSeedNodes);
                        progArgSeedNodes.remove(mySeedNodeAddress);
                    } else if (args.length > 5) {
                        log.error("Too many program arguments." +
                                "\nProgram arguments: myAddress (incl. port) bitcoinNetworkId " +
                                "maxConnections useLocalhost seedNodes (separated with |)");
                    }
                }
            }
        } catch (Throwable t) {
            shutDown();
        }
    }

    public void createAndStartP2PService(boolean useDetailedLogging) {
        createAndStartP2PService(mySeedNodeAddress, useLocalhost, Version.getBtcNetworkId(), useDetailedLogging, progArgSeedNodes, null);
    }

    @VisibleForTesting
    public void createAndStartP2PService(NodeAddress mySeedNodeAddress,
                                         boolean useLocalhost,
                                         int networkId,
                                         boolean useDetailedLogging,
                                         @Nullable Set<NodeAddress> progArgSeedNodes,
                                         @Nullable P2PServiceListener listener) {
        Path appPath = Paths.get(defaultUserDataDir,
                "Bitsquare_seed_node_" + String.valueOf(mySeedNodeAddress.getFullAddress().replace(":", "_")));

        String logPath = Paths.get(appPath.toString(), "logs").toString();
        Log.setup(logPath);
        log.info("Log files under: " + logPath);
        Version.printVersion();
        Utilities.printSysInfo();
        Log.setLevel(useDetailedLogging);

        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (progArgSeedNodes != null && !progArgSeedNodes.isEmpty()) {
            if (useLocalhost)
                seedNodesRepository.setLocalhostSeedNodeAddresses(progArgSeedNodes);
            else
                seedNodesRepository.setTorSeedNodeAddresses(progArgSeedNodes);
        }

        File storageDir = Paths.get(appPath.toString(), "db").toFile();
        if (storageDir.mkdirs())
            log.info("Created storageDir at " + storageDir.getAbsolutePath());

        File torDir = Paths.get(appPath.toString(), "tor").toFile();
        if (torDir.mkdirs())
            log.info("Created torDir at " + torDir.getAbsolutePath());

        seedNodesRepository.setNodeAddressToExclude(mySeedNodeAddress);
        seedNodeP2PService = new P2PService(seedNodesRepository, mySeedNodeAddress.port, torDir, useLocalhost, networkId, storageDir, new Clock(), null, null);
        seedNodeP2PService.start(listener);
    }

    @VisibleForTesting
    public P2PService getSeedNodeP2PService() {
        return seedNodeP2PService;
    }

    private void shutDown() {
        shutDown(null);
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        if (!stopped) {
            stopped = true;

            seedNodeP2PService.shutDown(() -> {
                if (shutDownCompleteHandler != null) UserThread.execute(shutDownCompleteHandler);
            });
        }
    }
}
