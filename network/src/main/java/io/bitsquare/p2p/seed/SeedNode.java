package io.bitsquare.p2p.seed;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.Clock;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.network.OptionKeys;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.peers.BanList;
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
    public static final int MAX_CONNECTIONS_LIMIT = 1000;
    public static final int MAX_CONNECTIONS_DEFAULT = 50;

    private NodeAddress mySeedNodeAddress = new NodeAddress("localhost:8001");
    private int maxConnections = MAX_CONNECTIONS_DEFAULT;  // we keep default a higher connection size for seed nodes
    private boolean useLocalhost = false;
    private Set<NodeAddress> progArgSeedNodes;
    private P2PService seedNodeP2PService;
    private boolean stopped;
    private final String defaultUserDataDir;
    private Level logLevel = Level.WARN;

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
    // localhost:3002 2 50 true
    // localhost:3002 2 50 localhost:4442|localhost:4443 true
    // Usage: -myAddress=<my onion address> -networkId=<networkId (Mainnet = 0, TestNet = 1, Regtest = 2)> -maxConnections=<Nr. of max. connections allowed> -useLocalhost=false -seedNodes=si3uu56adkyqkldl.onion:8002|eo5ay2lyzrfvx2nr.onion:8002 -ignore=4543y2lyzrfvx2nr.onion:8002|876572lyzrfvx2nr.onion:8002
    // Example usage: -myAddress=lmvdenjkyvx2ovga.onion:8001 -networkId=0 -maxConnections=20 -useLocalhost=false -seedNodes=si3uu56adkyqkldl.onion:8002|eo5ay2lyzrfvx2nr.onion:8002 -ignore=4543y2lyzrfvx2nr.onion:8002|876572lyzrfvx2nr.onion:8002

    public static final String USAGE = "Usage:\n" +
            "--myAddress=<my onion address>\n" +
            "--networkId=[0|1|2] (Mainnet = 0, TestNet = 1, Regtest = 2)\n" +
            "--maxConnections=<Nr. of max. connections allowed>\n" +
            "--useLocalhost=[true|false]\n" +
            "--logLevel=Log level [OFF, ALL, ERROR, WARN, INFO, DEBUG, TRACE]\n" +
            "--seedNodes=[onion addresses separated with comma]\n" +
            "--banList=[onion addresses separated with comma]\n" +
            "--help";

    public void processArgs(String[] args) {
        int networkId = -1;
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("--"))
                    arg = arg.substring(2);
                if (arg.startsWith(OptionKeys.MY_ADDRESS)) {
                    arg = arg.substring(OptionKeys.MY_ADDRESS.length() + 1);
                    checkArgument(arg.contains(":") && arg.split(":").length == 2 && arg.split(":")[1].length() > 3, "Wrong program argument: " + arg);
                    mySeedNodeAddress = new NodeAddress(arg);
                    log.info("From processArgs: mySeedNodeAddress=" + mySeedNodeAddress);
                } else if (arg.startsWith(OptionKeys.NETWORK_ID)) {
                    arg = arg.substring(OptionKeys.NETWORK_ID.length() + 1);
                    networkId = Integer.parseInt(arg);
                    log.info("From processArgs: networkId=" + networkId);
                    checkArgument(networkId > -1 && networkId < 3,
                            "networkId out of scope (Mainnet = 0, TestNet = 1, Regtest = 2)");
                    Version.setBtcNetworkId(networkId);
                } else if (arg.startsWith(OptionKeys.MAX_CONNECTIONS)) {
                    arg = arg.substring(OptionKeys.MAX_CONNECTIONS.length() + 1);
                    maxConnections = Integer.parseInt(arg);
                    log.info("From processArgs: maxConnections=" + maxConnections);
                    checkArgument(maxConnections < MAX_CONNECTIONS_LIMIT, "maxConnections seems to be a bit too high...");
                } else if (arg.startsWith(OptionKeys.USE_LOCALHOST)) {
                    arg = arg.substring(OptionKeys.USE_LOCALHOST.length() + 1);
                    checkArgument(arg.equals("true") || arg.equals("false"));
                    useLocalhost = ("true").equals(arg);
                    log.info("From processArgs: useLocalhost=" + useLocalhost);
                } else if (arg.startsWith(io.bitsquare.common.OptionKeys.LOG_LEVEL_KEY)) {
                    arg = arg.substring(io.bitsquare.common.OptionKeys.LOG_LEVEL_KEY.length() + 1);
                    logLevel = Level.toLevel(arg.toUpperCase());
                    log.info("From processArgs: logLevel=" + logLevel);
                } else if (arg.startsWith(OptionKeys.SEED_NODES_LIST)) {
                    arg = arg.substring(OptionKeys.SEED_NODES_LIST.length() + 1);
                    checkArgument(arg.contains(":") && arg.split(":").length > 1 && arg.split(":")[1].length() > 3,
                            "Wrong program argument " + arg);
                    List<String> list = Arrays.asList(arg.split(","));
                    progArgSeedNodes = new HashSet<>();
                    list.forEach(e -> {
                        checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4,
                                "Wrong program argument " + e);
                        progArgSeedNodes.add(new NodeAddress(e));
                    });
                    log.info("From processArgs: progArgSeedNodes=" + progArgSeedNodes);
                    progArgSeedNodes.remove(mySeedNodeAddress);
                } else if (arg.startsWith(OptionKeys.BAN_LIST)) {
                    arg = arg.substring(OptionKeys.BAN_LIST.length() + 1);
                    checkArgument(arg.contains(":") && arg.split(":").length > 1 && arg.split(":")[1].length() > 3,
                            "Wrong program argument " + arg);
                    List<String> list = Arrays.asList(arg.split(","));
                    list.forEach(e -> {
                        checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4,
                                "Wrong program argument " + e);
                        BanList.add(new NodeAddress(e));
                    });
                    log.info("From processArgs: ignoreList=" + list);
                } else if (arg.startsWith(OptionKeys.HELP)) {
                    log.info(USAGE);
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
        createAndStartP2PService(mySeedNodeAddress, maxConnections, useLocalhost,
                Version.getBtcNetworkId(), useDetailedLogging, progArgSeedNodes, null);
    }

    @VisibleForTesting
    public void createAndStartP2PService(NodeAddress mySeedNodeAddress,
                                         int maxConnections,
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
        Log.setLevel(logLevel);

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
        seedNodeP2PService = new P2PService(seedNodesRepository, mySeedNodeAddress.port, maxConnections,
                torDir, useLocalhost, networkId, storageDir, null, new Clock(), null, null);
        seedNodeP2PService.start(false, listener);
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
