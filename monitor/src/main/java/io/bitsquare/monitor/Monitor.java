package io.bitsquare.monitor;

import ch.qos.logback.classic.Level;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.trade.offer.OfferBookService;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bitcoinj.store.BlockStoreException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.nio.file.Paths;
import java.security.Security;

public class Monitor {
    private static final Logger log = LoggerFactory.getLogger(Monitor.class);
    private static Environment env;
    private final Injector injector;
    private final OfferBookService offerBookService;

    private P2PService p2pService;

    public static void setEnvironment(Environment env) {
        Monitor.env = env;
    }

    public Monitor() {
        String logPath = Paths.get(env.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY), "bitsquare").toString();
        Log.setup(logPath);
        log.info("Log files under: " + logPath);
        Version.printVersion();
        Utilities.printSysInfo();
        Log.setLevel(Level.toLevel(env.getRequiredProperty(io.bitsquare.common.OptionKeys.LOG_LEVEL_KEY)));

        // setup UncaughtExceptionHandler
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            // Might come from another thread 
            if (throwable.getCause() != null && throwable.getCause().getCause() != null &&
                    throwable.getCause().getCause() instanceof BlockStoreException) {
                log.error(throwable.getMessage());
            } else {
                log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
                log.error("throwableMessage= " + throwable.getMessage());
                log.error("throwableClass= " + throwable.getClass());
                log.error("Stack trace:\n" + ExceptionUtils.getStackTrace(throwable));
                throwable.printStackTrace();
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);

        if (Utilities.isRestrictedCryptography())
            Utilities.removeCryptographyRestrictions();
        Security.addProvider(new BouncyCastleProvider());


        MonitorModule monitorModule = new MonitorModule(env);
        injector = Guice.createInjector(monitorModule);
        Version.setBtcNetworkId(injector.getInstance(BitsquareEnvironment.class).getBitcoinNetwork().ordinal());
        p2pService = injector.getInstance(P2PService.class);
        offerBookService = injector.getInstance(OfferBookService.class);
        p2pService.start(false, null);
    }
}
