package io.bisq.statistics;

import ch.qos.logback.classic.Level;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bisq.common.CommonOptionKeys;
import io.bisq.common.GlobalSettings;
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
import io.bisq.core.offer.OfferBookService;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
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

public class Statistics {
    private static final Logger log = LoggerFactory.getLogger(Statistics.class);
    private static Environment env;
    private final Injector injector;
    private final StatisticsModule statisticsModule;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final OfferBookService offerBookService;
    private final PriceFeedService priceFeedService;

    private final P2PService p2pService;

    public static void setEnvironment(Environment env) {
        Statistics.env = env;
    }

    public Statistics() {
        String logPath = Paths.get(env.getProperty(AppOptionKeys.APP_DATA_DIR_KEY), "bisq").toString();
        Log.setup(logPath);
        log.info("Log files under: " + logPath);
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


        statisticsModule = new StatisticsModule(env);
        injector = Guice.createInjector(statisticsModule);
        Version.setBaseCryptoNetworkId(injector.getInstance(BisqEnvironment.class).getBaseCurrencyNetwork().ordinal());
        Version.printVersion();
        p2pService = injector.getInstance(P2PService.class);
        p2pService.start(new BootstrapListener() {
            @Override
            public void onBootstrapComplete() {
            }
        });

        // We want to persist trade statistics so we need to instantiate the tradeStatisticsManager
        tradeStatisticsManager = injector.getInstance(TradeStatisticsManager.class);
        offerBookService = injector.getInstance(OfferBookService.class);
        priceFeedService = injector.getInstance(PriceFeedService.class);

        // We need the price feed for market based offers
        priceFeedService.setCurrencyCode(GlobalSettings.getDefaultTradeCurrency().getCode());
        priceFeedService.requestPriceFeed(price -> log.debug("price " + price),
                (errorMessage, throwable) -> log.warn(throwable.getMessage()));
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
                injector.getInstance(OpenOfferManager.class).shutDown(() -> injector.getInstance(P2PService.class).shutDown(() -> {
                    injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                        statisticsModule.close(injector);
                        log.debug("Graceful shutdown completed");
                        resultHandler.handleResult();
                    });
                    injector.getInstance(WalletsSetup.class).shutDown();
                    injector.getInstance(BtcWalletService.class).shutDown();
                    injector.getInstance(BsqWalletService.class).shutDown();
                }));
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
