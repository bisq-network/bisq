package io.bitsquare.p2p.seed;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.peers.PeerGroup;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class SeedNode {
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);

    private Address mySeedNodeAddress = new Address("localhost:8001");
    private boolean useLocalhost = false;
    private Set<Address> seedNodes;
    private P2PService p2PService;
    private boolean stopped;

    public SeedNode() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // args: myAddress (incl. port) maxConnections useLocalhost seedNodes (separated with |)
    // 2. and 3. args are optional
    // eg. lmvdenjkyvx2ovga.onion:8001 20 false eo5ay2lyzrfvx2nr.onion:8002|si3uu56adkyqkldl.onion:8003
    // or when using localhost:  localhost:8001 20 true localhost:8002|localhost:8003
    public void processArgs(String[] args) {
        if (args.length > 0) {
            String arg0 = args[0];
            checkArgument(arg0.contains(":") && arg0.split(":").length == 2 && arg0.split(":")[1].length() == 4, "Wrong program argument");
            mySeedNodeAddress = new Address(arg0);
            if (args.length > 1) {
                String arg1 = args[1];
                int maxConnections = Integer.parseInt(arg1);
                checkArgument(maxConnections < 1000, "maxConnections seems to be a bit too high...");
                PeerGroup.setMaxConnections(maxConnections);
                if (args.length > 2) {
                    String arg2 = args[2];
                    checkArgument(arg2.equals("true") || arg2.equals("false"));
                    useLocalhost = ("true").equals(arg2);
                }
                if (args.length > 3) {
                    String arg3 = args[3];
                    checkArgument(arg3.contains(":") && arg3.split(":").length > 1 && arg3.split(":")[1].length() > 3, "Wrong program argument");
                    List<String> list = Arrays.asList(arg3.split("|"));
                    seedNodes = new HashSet<>();
                    list.forEach(e -> {
                        checkArgument(e.contains(":") && e.split(":").length == 2 && e.split(":")[1].length() == 4, "Wrong program argument");
                        seedNodes.add(new Address(e));
                    });
                    seedNodes.remove(mySeedNodeAddress);
                } else if (args.length > 4) {
                    log.error("Too many program arguments." +
                            "\nProgram arguments: myAddress useLocalhost seedNodes");
                }
            }
        }
    }

    public void createAndStartP2PService() {
        createAndStartP2PService(null, null, mySeedNodeAddress, useLocalhost, seedNodes, null);
    }

    public void createAndStartP2PService(EncryptionService encryptionService, KeyRing keyRing, Address mySeedNodeAddress, boolean useLocalhost, @Nullable Set<Address> seedNodes, @Nullable P2PServiceListener listener) {
        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (seedNodes != null && !seedNodes.isEmpty()) {
            if (useLocalhost)
                seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
            else
                seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
        }

        p2PService = new P2PService(seedNodesRepository, mySeedNodeAddress.port, new File("bitsquare_seed_node_" + mySeedNodeAddress.port), useLocalhost, encryptionService, keyRing, new File("dummy"));
        p2PService.removeMySeedNodeAddressFromList(mySeedNodeAddress);
        p2PService.start(listener);
    }

    public P2PService getP2PService() {
        return p2PService;
    }

    public void shutDown() {
        shutDown(null);
    }

    public void shutDown(@Nullable Runnable shutDownCompleteHandler) {
        log.debug("Request shutdown seed node");
        if (!stopped) {
            stopped = true;

            p2PService.shutDown(() -> {
                if (shutDownCompleteHandler != null) UserThread.execute(shutDownCompleteHandler);
            });
        }
    }
}
