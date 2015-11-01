package io.bitsquare.p2p.seed;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.NoSuchAlgorithmException;
import java.security.Security;

public class SeedNodeMain {

    // args: port useLocalhost seedNodes
    // eg. 4444 true localhost:7777 localhost:8888 
    // To stop enter: q
    public static void main(String[] args) throws NoSuchAlgorithmException {
        Security.addProvider(new BouncyCastleProvider());
        
        SeedNode seedNode = new SeedNode();
        seedNode.processArgs(args);
        seedNode.createAndStartP2PService();
        seedNode.listenForExitCommand();
    }
}
