package io.bitsquare.p2p.seed;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.bitsquare.app.Logging;
import io.bitsquare.common.UserThread;
import org.bitcoinj.crypto.DRMWorkaround;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Scanner;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class SeedNodeMain {
    private static final Logger log = LoggerFactory.getLogger(SeedNodeMain.class);
    private SeedNode seedNode;

    private boolean stopped;

    // args: myAddress (incl. port) useLocalhost seedNodes (separated with |)
    // eg. lmvdenjkyvx2ovga.onion:8001 false eo5ay2lyzrfvx2nr.onion:8002|si3uu56adkyqkldl.onion:8003
    // To stop enter: q
    public static void main(String[] args) throws NoSuchAlgorithmException {
        Path path = Paths.get("seed_node_log");
        Logging.setup(path.toString());
        log.info("Log files under: " + path.toAbsolutePath().toString());

        DRMWorkaround.maybeDisableExportControls();
        
        new SeedNodeMain(args);
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
                    Timer timeout = UserThread.runAfter(() -> {
                        log.error("Timeout occurred at shutDown request");
                        System.exit(1);
                    }, 5);

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
