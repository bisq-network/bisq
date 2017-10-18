package io.bisq.api.app;

import ch.qos.logback.classic.Level;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bisq.api.service.BisqApiApplication;
import io.bisq.common.CommonOptionKeys;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.LimitedKeyStrengthException;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.*;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.OfferBookService;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.user.User;
import io.bisq.network.p2p.P2PService;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bitcoinj.store.BlockStoreException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

public class Api {
    private static final Logger log = LoggerFactory.getLogger(Api.class);
    private static Environment env;
    private final Injector injector;
    private final OfferBookService offerBookService;
    private final OpenOfferManager openOfferManager;
    private final ApiModule apiModule;
    private final AppSetup appSetup;
    private final User user;

    public static void setEnvironment(Environment env) {
        Api.env = env;
    }

    public Api() {
        String logPath = Paths.get(env.getProperty(AppOptionKeys.APP_DATA_DIR_KEY), "bisq").toString();
        Log.setup(logPath);
        log.info("Log files under: " + logPath);
        Version.printVersion();
        Utilities.printSysInfo();
        Log.setLevel(Level.toLevel(env.getRequiredProperty(CommonOptionKeys.LOG_LEVEL_KEY)));

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

        try {
            Utilities.checkCryptoPolicySetup();
        } catch (NoSuchAlgorithmException | LimitedKeyStrengthException e) {
            e.printStackTrace();
            UserThread.execute(this::shutDown);
        }
        Security.addProvider(new BouncyCastleProvider());

        apiModule = new ApiModule(env);
        injector = Guice.createInjector(apiModule);
        Version.setBaseCryptoNetworkId(injector.getInstance(BisqEnvironment.class).getBaseCurrencyNetwork().ordinal());
        offerBookService = injector.getInstance(OfferBookService.class);
        user = injector.getInstance(User.class);
        openOfferManager = injector.getInstance(OpenOfferManager.class);

        appSetup = injector.getInstance(AppSetupWithP2P.class);
        appSetup.start();

        try {

            injector.getInstance(BisqApiApplication.class).run("server", "bisq-api.yml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutDown() {
        gracefulShutDown(() -> {
            log.debug("Shutdown complete");
            System.exit(0);
        });
    }

    public void gracefulShutDown(ResultHandler resultHandler) {
        log.debug("gracefulShutDown");
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        injector.getInstance(BtcWalletService.class).shutDown();
                    });
                });
                // we wait max 5 sec.
                UserThread.runAfter(resultHandler::handleResult, 5);
            } else {
                UserThread.runAfter(resultHandler::handleResult, 1);
            }
        } catch (Throwable t) {
            log.debug("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
