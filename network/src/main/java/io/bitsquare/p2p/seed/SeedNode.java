package io.bitsquare.p2p.seed;

import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.peers.PeerGroup;
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

    private Address mySeedNodeAddress = new Address("localhost:8001");
    private boolean useLocalhost = false;
    private Set<Address> progArgSeedNodes;
    private P2PService p2PService;
    private boolean stopped;
    private final String defaultUserDataDir;

    public SeedNode(String defaultUserDataDir) {
        this.defaultUserDataDir = defaultUserDataDir;
        Log.traceCall();
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
        Log.traceCall();
        try {
            if (args.length > 0) {
                String arg0 = args[0];
                checkArgument(arg0.contains(":") && arg0.split(":").length == 2 && arg0.split(":")[1].length() > 3, "Wrong program argument: " + arg0);
                mySeedNodeAddress = new Address(arg0);
                if (args.length > 1) {
                    String arg1 = args[1];
                    int networkId = Integer.parseInt(arg1);
                    checkArgument(networkId > -1 && networkId < 3,
                            "networkId out of scope (Mainnet = 0, TestNet = 1, Regtest = 2)");
                    Version.NETWORK_ID = networkId;
                    if (args.length > 2) {
                        String arg2 = args[2];
                        int maxConnections = Integer.parseInt(arg2);
                        checkArgument(maxConnections < 1000, "maxConnections seems to be a bit too high...");
                        PeerGroup.setMaxConnectionsLowPrio(maxConnections);
                    } else {
                        // we keep default a higher connection size for seed nodes
                        PeerGroup.setMaxConnectionsLowPrio(50);
                    }
                    if (args.length > 3) {
                        String arg3 = args[3];
                        checkArgument(arg3.equals("true") || arg3.equals("false"));
                        useLocalhost = ("true").equals(arg3);
                    }
                    if (args.length > 4) {
                        String arg4 = args[4];
                        checkArgument(arg4.contains(":") && arg4.split(":").length > 1 && arg4.split(":")[1].length() > 3,
                                "Wrong program argument");
                        List<String> list = Arrays.asList(arg4.split("|"));
                        progArgSeedNodes = new HashSet<>();
                        list.forEach(e -> {
                            checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4,
                                    "Wrong program argument");
                            progArgSeedNodes.add(new Address(e));
                        });
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

    public void createAndStartP2PService() {
        createAndStartP2PService(mySeedNodeAddress, useLocalhost, Version.NETWORK_ID, progArgSeedNodes, null);
    }

    public void createAndStartP2PService(Address mySeedNodeAddress,
                                         boolean useLocalhost,
                                         int networkId,
                                         @Nullable Set<Address> progArgSeedNodes,
                                         @Nullable P2PServiceListener listener) {
        Log.traceCall();
        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (progArgSeedNodes != null && !progArgSeedNodes.isEmpty()) {
            if (useLocalhost)
                seedNodesRepository.setLocalhostSeedNodeAddresses(progArgSeedNodes);
            else
                seedNodesRepository.setTorSeedNodeAddresses(progArgSeedNodes);
        }
        Path seedNodePath = Paths.get(defaultUserDataDir,
                "Bitsquare_seed_node_" + String.valueOf(mySeedNodeAddress.getFullAddress().replace(":", "_")));
        File storageDir = Paths.get(seedNodePath.toString(), "db").toFile();
        File torDir = Paths.get(seedNodePath.toString(), "tor").toFile();

        if (storageDir.mkdirs())
            log.info("Created storageDir at " + storageDir.getAbsolutePath());
        if (torDir.mkdirs())
            log.info("Created torDir at " + torDir.getAbsolutePath());

        p2PService = new P2PService(seedNodesRepository, mySeedNodeAddress.port, torDir, useLocalhost, networkId, storageDir);
        p2PService.removeMySeedNodeAddressFromList(mySeedNodeAddress);
        p2PService.start(listener);
    }

    public P2PService getP2PService() {
        Log.traceCall();
        return p2PService;
    }

    public void shutDown() {
        Log.traceCall();
        shutDown(null);
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        Log.traceCall();
        log.debug("Request shutdown seed node");
        if (!stopped) {
            stopped = true;

            p2PService.shutDown(() -> {
                if (shutDownCompleteHandler != null) UserThread.execute(shutDownCompleteHandler);
            });
        }
    }
}
