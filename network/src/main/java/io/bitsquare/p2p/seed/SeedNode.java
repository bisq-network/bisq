package io.bitsquare.p2p.seed;

import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class SeedNode {
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);

    private int port = 8001;
    private boolean useLocalhost = true;
    private List<Address> seedNodes;
    private P2PService p2PService;
    protected boolean stopped;

    public SeedNode() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // args: port useLocalhost seedNodes
    // eg. 4444 true localhost:7777 localhost:8888 
    // To stop enter: q
    public void processArgs(String[] args) {
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);

            if (args.length > 1) {
                useLocalhost = ("true").equals(args[1]);

                if (args.length > 2) {
                    seedNodes = new ArrayList<>();
                    for (int i = 2; i < args.length; i++) {
                        seedNodes.add(new Address(args[i]));
                    }
                }
            }
        }
    }

    public void listenForExitCommand() {
        Scanner scan = new Scanner(System.in);
        String line;
        while (!stopped && ((line = scan.nextLine()) != null)) {
            if (line.equals("q")) {
                Timer timeout = new Timer();
                timeout.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        log.error("Timeout occurred at shutDown request");
                        System.exit(1);
                    }
                }, 10 * 1000);

                shutDown(() -> {
                    timeout.cancel();
                    log.debug("Shutdown seed node complete.");
                    System.exit(0);
                });
            }
        }
    }

    public void createAndStartP2PService() {
        createAndStartP2PService(null, null, port, useLocalhost, seedNodes, null);
    }

    public void createAndStartP2PService(EncryptionService encryptionService, KeyRing keyRing, int port, boolean useLocalhost, @Nullable List<Address> seedNodes, @Nullable P2PServiceListener listener) {
        SeedNodesRepository seedNodesRepository = new SeedNodesRepository();
        if (seedNodes != null && !seedNodes.isEmpty()) {
            if (useLocalhost)
                seedNodesRepository.setLocalhostSeedNodeAddresses(seedNodes);
            else
                seedNodesRepository.setTorSeedNodeAddresses(seedNodes);
        }

        p2PService = new P2PService(seedNodesRepository, port, new File("bitsquare_seed_node_" + port), useLocalhost, encryptionService, keyRing);
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
                if (shutDownCompleteHandler != null) new Thread(shutDownCompleteHandler).start();
            });
        }
    }
}
