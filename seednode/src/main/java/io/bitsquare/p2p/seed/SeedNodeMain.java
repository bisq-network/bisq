package io.bitsquare.p2p.seed;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.common.UserThread;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class SeedNodeMain {
    private static final Logger log = LoggerFactory.getLogger(SeedNodeMain.class);

    public static final boolean USE_DETAILED_LOGGING = true;

    private SeedNode seedNode;

    // args: myAddress (incl. port) useLocalhost seedNodes (separated with |)
    // eg. lmvdenjkyvx2ovga.onion:8001 false eo5ay2lyzrfvx2nr.onion:8002|si3uu56adkyqkldl.onion:8003
    public static void main(String[] args) throws InterruptedException {
        new SeedNodeMain(args);
    }

    public SeedNodeMain(String[] args) throws InterruptedException {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("SeedNodeMain")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        // setup UncaughtExceptionHandler
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            // Might come from another thread 
            log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
            log.error("Uncaught Exception throwableMessage= " + throwable.getMessage());
            throwable.printStackTrace();
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);

        Security.addProvider(new BouncyCastleProvider());

        UserThread.execute(() -> {
            try {
                seedNode = new SeedNode(BitsquareEnvironment.defaultUserDataDir());
                seedNode.processArgs(args);
                seedNode.createAndStartP2PService(USE_DETAILED_LOGGING);
            } catch (Throwable t) {
                log.error("Executing task failed. " + t.getMessage());
                t.printStackTrace();
            }
        });

        while (true) {
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}
