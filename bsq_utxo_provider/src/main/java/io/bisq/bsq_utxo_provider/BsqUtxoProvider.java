package io.bisq.bsq_utxo_provider;

import ch.qos.logback.classic.Level;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bisq.common.CommonOptionKeys;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.LimitedKeyStrengthException;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.network.p2p.BootstrapListener;
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

public class BsqUtxoProvider {
    private static final Logger log = LoggerFactory.getLogger(BsqUtxoProvider.class);
    private static Environment env;
    private final Injector injector;
    private final BsqUtxoProviderModule statisticsModule;

    private final P2PService p2pService;
    private final BsqBlockchainManager bsqBlockchainManager;

    public static void setEnvironment(Environment env) {
        BsqUtxoProvider.env = env;
    }

    public BsqUtxoProvider() {
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


        statisticsModule = new BsqUtxoProviderModule(env);
        injector = Guice.createInjector(statisticsModule);
        Version.setBtcNetworkId(injector.getInstance(BisqEnvironment.class).getBitcoinNetwork().ordinal());
        p2pService = injector.getInstance(P2PService.class);
        p2pService.start(new BootstrapListener() {
            @Override
            public void onBootstrapComplete() {
            }
        });

        //TODO
        // blockchainRpcService = injector.getInstance(BsqBlockchainRpcService.class);
        bsqBlockchainManager = injector.getInstance(BsqBlockchainManager.class);
        bsqBlockchainManager.onAllServicesInitialized(log::error);
    }

    private void shutDown() {
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
                        injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                            statisticsModule.close(injector);
                            log.debug("Graceful shutdown completed");
                            resultHandler.handleResult();
                        });
                        injector.getInstance(WalletsSetup.class).shutDown();
                        injector.getInstance(BtcWalletService.class).shutDown();
                        injector.getInstance(BsqWalletService.class).shutDown();
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
