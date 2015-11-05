package io.bitsquare.p2p.seed;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import org.bitcoinj.crypto.DRMWorkaround;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Random;
import java.util.Scanner;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class SeedNodeMain {
    private static final Logger log = LoggerFactory.getLogger(SeedNodeMain.class);
    private static SeedNodeMain seedNodeMain;
    private SeedNode seedNode;

    private boolean stopped;

    // args: port useLocalhost seedNodes
    // eg. 4444 true localhost:7777 localhost:8888 
    // To stop enter: q
    public static void main(String[] args) throws NoSuchAlgorithmException {

        DRMWorkaround.maybeDisableExportControls();
        seedNodeMain = new SeedNodeMain(args);
    }

    public SeedNodeMain(String[] args) {
        Security.addProvider(new BouncyCastleProvider());

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("SeedNodeMain")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));
        UserThread.execute(() -> {
            try {
                seedNode = new SeedNode();
                seedNode.processArgs(args);
                seedNode.createAndStartP2PService();
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        });
        listenForExitCommand();
    }

    public void listenForExitCommand() {
        Scanner scan = new Scanner(System.in);
        String line;
        while (!stopped && ((line = scan.nextLine()) != null)) {
            if (line.equals("q")) {
                if (!stopped) {
                    stopped = true;
                    Timer timeout = Utilities.runTimerTask(() -> {
                        Thread.currentThread().setName("ShutdownTimeout-" + new Random().nextInt(1000));
                        log.error("Timeout occurred at shutDown request");
                        System.exit(1);
                    }, 10);

                    if (seedNode != null) {
                        seedNode.shutDown(() -> {
                            timeout.cancel();
                            log.debug("Shutdown seed node complete.");
                            System.exit(0);
                        });
                    }
                }
            }
        }
    }
}
