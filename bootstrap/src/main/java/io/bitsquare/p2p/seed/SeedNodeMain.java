package io.bitsquare.p2p.seed;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.bitsquare.common.UserThread;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class SeedNodeMain {

    // args: port useLocalhost seedNodes
    // eg. 4444 true localhost:7777 localhost:8888 
    // To stop enter: q
    public static void main(String[] args) throws NoSuchAlgorithmException {
        Security.addProvider(new BouncyCastleProvider());

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("SeedNodeMain")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        SeedNode seedNode = new SeedNode();
        seedNode.processArgs(args);
        seedNode.createAndStartP2PService();
        seedNode.listenForExitCommand();
    }
}
