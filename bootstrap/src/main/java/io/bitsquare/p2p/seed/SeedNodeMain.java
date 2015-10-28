package io.bitsquare.p2p.seed;

import java.security.NoSuchAlgorithmException;

public class SeedNodeMain {

    // args: port useLocalhost seedNodes
    // eg. 4444 true localhost:7777 localhost:8888 
    // To stop enter: q
    public static void main(String[] args) throws NoSuchAlgorithmException {
        SeedNode seedNode = new SeedNode();
        seedNode.processArgs(args);
        seedNode.createAndStartP2PService();
        seedNode.listenForExitCommand();
    }
}
