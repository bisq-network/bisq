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

package bisq.network.p2p;

import bisq.common.UserThread;
import bisq.common.app.Log;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.util.Utilities;

import com.google.common.annotations.VisibleForTesting;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import org.jetbrains.annotations.Nullable;

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
        this.defaultUserDataDir = defaultUserDataDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // args: bitcoinNetworkId maxConnections useLocalhostForP2P seedNodes (separated with |)
    // 2. and 3. args are optional
    // eg. lmvdenjkyvx2ovga.onion:8001 0 20 false eo5ay2lyzrfvx2nr.onion:8002|si3uu56adkyqkldl.onion:8003
    // or when using localhost:  localhost:8001 2 20 true localhost:8002|localhost:8003
    // BitcoinNetworkId: The id for the bitcoin network (Mainnet = 0, TestNet = 1, Regtest = 2)
    // localhost:3002 2 50 true
    // localhost:3002 2 50 localhost:4442|localhost:4443 true
    // Usage: -networkId=<networkId (Mainnet = 0, TestNet = 1, Regtest = 2)> -maxConnections=<No. of max. connections allowed> -useLocalhostForP2P=false -seedNodes=si3uu56adkyqkldl.onion:8002|eo5ay2lyzrfvx2nr.onion:8002 -ignore=4543y2lyzrfvx2nr.onion:8002|876572lyzrfvx2nr.onion:8002
    // Example usage: -networkId=0 -maxConnections=20 -useLocalhostForP2P=false -seedNodes=si3uu56adkyqkldl.onion:8002|eo5ay2lyzrfvx2nr.onion:8002 -ignore=4543y2lyzrfvx2nr.onion:8002|876572lyzrfvx2nr.onion:8002

    public static final String USAGE = "Usage:\n" +
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
                if (arg.startsWith("networkId")) {
                    arg = arg.substring("networkId".length() + 1);
                    networkId = Integer.parseInt(arg);
                    log.debug("From processArgs: networkId=" + networkId);
                    checkArgument(networkId > -1 && networkId < 3,
                            "networkId out of scope (Mainnet = 0, TestNet = 1, Regtest = 2)");
                    Version.setBaseCryptoNetworkId(networkId);
                } else if (arg.startsWith(Config.MAX_CONNECTIONS)) {
                    arg = arg.substring(Config.MAX_CONNECTIONS.length() + 1);
                    maxConnections = Integer.parseInt(arg);
                    log.debug("From processArgs: maxConnections=" + maxConnections);
                    checkArgument(maxConnections < MAX_CONNECTIONS_LIMIT, "maxConnections seems to be a bit too high...");
                } else if (arg.startsWith(Config.USE_LOCALHOST_FOR_P2P)) {
                    arg = arg.substring(Config.USE_LOCALHOST_FOR_P2P.length() + 1);
                    checkArgument(arg.equals("true") || arg.equals("false"));
                    useLocalhostForP2P = ("true").equals(arg);
                    log.debug("From processArgs: useLocalhostForP2P=" + useLocalhostForP2P);
                } else if (arg.startsWith(Config.LOG_LEVEL)) {
                    arg = arg.substring(Config.LOG_LEVEL.length() + 1);
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
                } else if (arg.startsWith(Config.BAN_LIST)) {
                    arg = arg.substring(Config.BAN_LIST.length() + 1);
                    checkArgument(arg.contains(":") && arg.split(":").length > 1 && arg.split(":")[1].length() > 3,
                            "Wrong program argument " + arg);
                    List<String> list = Arrays.asList(arg.split(","));
                    list.forEach(e -> {
                        checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4,
                                "Wrong program argument " + e);
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

       /* SeedNodeRepository seedNodesRepository = new SeedNodeRepository();
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
                torDir, useLocalhostForP2P, networkId, storageDir, null, null, null, new ClockWatcher(), null, null,
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
