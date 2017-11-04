package io.bisq.network.p2p;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import io.bisq.common.CommonOptionKeys;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.app.Version;
import io.bisq.common.util.Utilities;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.peers.BanList;
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

// Previously used seednode class, replaced now by bootstrap module. We keep it here as it was used in tests...
@SuppressWarnings("ALL")
public class DummySeedNode {
    private static final Logger log = LoggerFactory.getLogger(DummySeedNode.class);
    public static final int MAX_CONNECTIONS_LIMIT = 1000;
    public static final int MAX_CONNECTIONS_DEFAULT = 50;
    public static final String SEED_NODES_LIST = "seedNodes";
    public static final String HELP = "help";
    private NodeAddress mySeedNodeAddress = new NodeAddress("localhost:8001");
    private int maxConnections = MAX_CONNECTIONS_DEFAULT;  // we keep default a higher connection size for seed nodes
    private boolean useLocalhostForP2P = false;
    private Set<NodeAddress> progArgSeedNodes;
    private P2PService seedNodeP2PService;
    private boolean stopped;
    private final String defaultUserDataDir;
    private Level logLevel = Level.WARN;

    public DummySeedNode(String defaultUserDataDir) {
        Log.traceCall("defaultUserDataDir=" + defaultUserDataDir);
        this.defaultUserDataDir = defaultUserDataDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // args: myAddress (incl. port) bitcoinNetworkId maxConnections useLocalhostForP2P seedNodes (separated with |)
    // 2. and 3. args are optional
    // eg. lmvdenjkyvx2ovga.onion:8001 0 20 false eo5ay2lyzrfvx2nr.onion:8002|si3uu56adkyqkldl.onion:8003
    // or when using localhost:  localhost:8001 2 20 true localhost:8002|localhost:8003
    // BitcoinNetworkId: The id for the bitcoin network (Mainnet = 0, TestNet = 1, Regtest = 2)
    // localhost:3002 2 50 true
    // localhost:3002 2 50 localhost:4442|localhost:4443 true
    // Usage: -myAddress=<my onion address> -networkId=<networkId (Mainnet = 0, TestNet = 1, Regtest = 2)> -maxConnections=<No. of max. connections allowed> -useLocalhostForP2P=false -seedNodes=si3uu56adkyqkldl.onion:8002|eo5ay2lyzrfvx2nr.onion:8002 -ignore=4543y2lyzrfvx2nr.onion:8002|876572lyzrfvx2nr.onion:8002
    // Example usage: -myAddress=lmvdenjkyvx2ovga.onion:8001 -networkId=0 -maxConnections=20 -useLocalhostForP2P=false -seedNodes=si3uu56adkyqkldl.onion:8002|eo5ay2lyzrfvx2nr.onion:8002 -ignore=4543y2lyzrfvx2nr.onion:8002|876572lyzrfvx2nr.onion:8002

    public static final String USAGE = "Usage:\n" +
            "--myAddress=<my onion address>\n" +
            "--networkId=[0|1|2] (Mainnet = 0, TestNet = 1, Regtest = 2)\n" +
            "--maxConnections=<No. of max. connections allowed>\n" +
            "--useLocalhostForP2P=[true|false]\n" +
            "--logLevel=Log level [OFF, ALL, ERROR, WARN, INFO, DEBUG, TRACE]\n" +
            "--seedNodes=[onion addresses separated with comma]\n" +
            "--banList=[onion addresses separated with comma]\n" +
            "--help";

    public void processArgs(String[] args) {
        int networkId = -1;
        try {
            for (String arg1 : args) {
                String arg = arg1;
                if (arg.startsWith("--"))
                    arg = arg.substring(2);
                if (arg.startsWith(NetworkOptionKeys.MY_ADDRESS)) {
                    arg = arg.substring(NetworkOptionKeys.MY_ADDRESS.length() + 1);
                    checkArgument(arg.contains(":") && arg.split(":").length == 2 && arg.split(":")[1].length() > 3, "Wrong program argument: " + arg);
                    mySeedNodeAddress = new NodeAddress(arg);
                    log.debug("From processArgs: mySeedNodeAddress=" + mySeedNodeAddress);
                } else if (arg.startsWith(NetworkOptionKeys.NETWORK_ID)) {
                    arg = arg.substring(NetworkOptionKeys.NETWORK_ID.length() + 1);
                    networkId = Integer.parseInt(arg);
                    log.debug("From processArgs: networkId=" + networkId);
                    checkArgument(networkId > -1 && networkId < 3,
                            "networkId out of scope (Mainnet = 0, TestNet = 1, Regtest = 2)");
                    Version.setBaseCryptoNetworkId(networkId);
                } else if (arg.startsWith(NetworkOptionKeys.MAX_CONNECTIONS)) {
                    arg = arg.substring(NetworkOptionKeys.MAX_CONNECTIONS.length() + 1);
                    maxConnections = Integer.parseInt(arg);
                    log.debug("From processArgs: maxConnections=" + maxConnections);
                    checkArgument(maxConnections < MAX_CONNECTIONS_LIMIT, "maxConnections seems to be a bit too high...");
                } else if (arg.startsWith(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P)) {
                    arg = arg.substring(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P.length() + 1);
                    checkArgument(arg.equals("true") || arg.equals("false"));
                    useLocalhostForP2P = ("true").equals(arg);
                    log.debug("From processArgs: useLocalhostForP2P=" + useLocalhostForP2P);
                } else if (arg.startsWith(CommonOptionKeys.LOG_LEVEL_KEY)) {
                    arg = arg.substring(CommonOptionKeys.LOG_LEVEL_KEY.length() + 1);
                    logLevel = Level.toLevel(arg.toUpperCase());
                    log.debug("From processArgs: logLevel=" + logLevel);
                } else if (arg.startsWith(SEED_NODES_LIST)) {
                    arg = arg.substring(SEED_NODES_LIST.length() + 1);
                    checkArgument(arg.contains(":") && arg.split(":").length > 1 && arg.split(":")[1].length() > 3,
                            "Wrong program argument " + arg);
                    List<String> list = Arrays.asList(arg.split(","));
                    progArgSeedNodes = new HashSet<>();
                    list.forEach(e -> {
                        checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4,
                                "Wrong program argument " + e);
                        progArgSeedNodes.add(new NodeAddress(e));
                    });
                    log.debug("From processArgs: progArgSeedNodes=" + progArgSeedNodes);
                    progArgSeedNodes.remove(mySeedNodeAddress);
                } else if (arg.startsWith(NetworkOptionKeys.BAN_LIST)) {
                    arg = arg.substring(NetworkOptionKeys.BAN_LIST.length() + 1);
                    checkArgument(arg.contains(":") && arg.split(":").length > 1 && arg.split(":")[1].length() > 3,
                            "Wrong program argument " + arg);
                    List<String> list = Arrays.asList(arg.split(","));
                    list.forEach(e -> {
                        checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4,
                                "Wrong program argument " + e);
                        BanList.add(new NodeAddress(e));
                    });
                    log.debug("From processArgs: ignoreList=" + list);
                } else if (arg.startsWith(HELP)) {
                    log.debug(USAGE);
                } else {
                    log.error("Invalid argument. " + arg + "\n" + USAGE);
                }
            }

            if (mySeedNodeAddress == null)
                log.error("My seed node must be set.\n" + USAGE);
            if (networkId == -1)
                log.error("NetworkId must be set.\n" + USAGE);

        } catch (Throwable t) {
            log.error("Some arguments caused an exception. " + Arrays.toString(args) + "\nException: " + t.getMessage() + "\n" + USAGE);
            shutDown();
        }
    }

    public void createAndStartP2PService(boolean useDetailedLogging) {
        createAndStartP2PService(mySeedNodeAddress, maxConnections, useLocalhostForP2P,
                Version.getBaseCurrencyNetwork(), useDetailedLogging, progArgSeedNodes, null);
    }

    @VisibleForTesting
    public void createAndStartP2PService(NodeAddress mySeedNodeAddress,
                                         int maxConnections,
                                         boolean useLocalhostForP2P,
                                         int networkId,
                                         @SuppressWarnings("UnusedParameters") boolean useDetailedLogging,
                                         @Nullable Set<NodeAddress> progArgSeedNodes,
                                         @Nullable P2PServiceListener listener) {
        Path appPath = Paths.get(defaultUserDataDir,
                "bisq_seed_node_" + String.valueOf(mySeedNodeAddress.getFullAddress().replace(":", "_")));

        String logPath = Paths.get(appPath.toString(), "logs").toString();
        Log.setup(logPath);
        log.debug("Log files under: " + logPath);
        Version.printVersion();
        Utilities.printSysInfo();
        Log.setLevel(logLevel);

       /* SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (progArgSeedNodes != null && !progArgSeedNodes.isEmpty()) {
            if (useLocalhostForP2P)
                seedNodesRepository.setLocalhostSeedNodeAddresses(progArgSeedNodes);
            else
                seedNodesRepository.setTorSeedNodeAddresses(progArgSeedNodes);
        }*/

        File storageDir = Paths.get(appPath.toString(), "db").toFile();
        if (storageDir.mkdirs())
            log.debug("Created storageDir at " + storageDir.getAbsolutePath());

        File torDir = Paths.get(appPath.toString(), "tor").toFile();
        if (torDir.mkdirs())
            log.debug("Created torDir at " + torDir.getAbsolutePath());

        // seedNodesRepository.setNodeAddressToExclude(mySeedNodeAddress);
      /*  seedNodeP2PService = new P2PService(seedNodesRepository, mySeedNodeAddress.getPort(), maxConnections,
                torDir, useLocalhostForP2P, networkId, storageDir, null, null, null, new Clock(), null, null,
                null, TestUtils.getNetworkProtoResolver(), TestUtils.getPersistenceProtoResolver());
        seedNodeP2PService.start(listener);*/
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
