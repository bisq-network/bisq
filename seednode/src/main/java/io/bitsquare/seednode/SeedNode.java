package io.bitsquare.seednode;

import ch.qos.logback.classic.Level;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.app.BitsquareEnvironment;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.CommonOptionKeys;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.trade.offer.OpenOfferManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bitcoinj.store.BlockStoreException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.nio.file.Paths;
import java.security.Security;

public class SeedNode {
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);
    private static Environment env;
    private final Injector injector;
    private final SeedNodeModule seedNodeModule;

    private P2PService p2pService;

    public static void setEnvironment(Environment env) {
        SeedNode.env = env;
    }

    public SeedNode() {
        String logPath = Paths.get(env.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY), "bitsquare").toString();
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

        if (Utilities.isRestrictedCryptography())
            Utilities.removeCryptographyRestrictions();
        Security.addProvider(new BouncyCastleProvider());


        seedNodeModule = new SeedNodeModule(env);
        injector = Guice.createInjector(seedNodeModule);
        Version.setBtcNetworkId(injector.getInstance(BitsquareEnvironment.class).getBitcoinNetwork().ordinal());
        p2pService = injector.getInstance(P2PService.class);
        p2pService.start(false, new P2PServiceListener() {
            @Override
            public void onRequestingDataCompleted() {
            }

            @Override
            public void onNoSeedNodeAvailable() {

            }

            @Override
            public void onNoPeersAvailable() {

            }

            @Override
            public void onBootstrapComplete() {

            }

            @Override
            public void onTorNodeReady() {

            }

            @Override
            public void onHiddenServicePublished() {

            }

            @Override
            public void onSetupFailed(Throwable throwable) {

            }

            @Override
            public void onUseDefaultBridges() {

            }

            @Override
            public void onRequestCustomBridges(Runnable resultHandler) {

            }
        });
    }

    public void shutDown() {
        gracefulShutDown(() -> {
            log.info("Shutdown complete");
            System.exit(0);
        });
    }

    private void gracefulShutDown(ResultHandler resultHandler) {
        log.debug("gracefulShutDown");
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        injector.getInstance(WalletService.class).shutDownDone.addListener((ov, o, n) -> {
                            seedNodeModule.close(injector);
                            log.info("Graceful shutdown completed");
                            resultHandler.handleResult();
                        });
                        injector.getInstance(WalletService.class).shutDown();
                    });
                });
                // we wait max 5 sec.
                UserThread.runAfter(resultHandler::handleResult, 5);
            } else {
                UserThread.runAfter(resultHandler::handleResult, 1);
            }
        } catch (Throwable t) {
            log.info("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
