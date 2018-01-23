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
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.AppSetup;
import io.bisq.core.app.AppSetupWithP2P;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.BaseCurrencyNetwork;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletsManager;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.dao.vote.VotingManager;
import io.bisq.core.offer.OfferBookService;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.gui.Navigation;
import io.bisq.network.p2p.P2PService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bitcoinj.store.BlockStoreException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;

public class Api {
    private static final Logger log = LoggerFactory.getLogger(Api.class);
    private static Environment env;
    private final Injector injector;
    private final OfferBookService offerBookService;
    private final OpenOfferManager openOfferManager;
    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final ApiModule apiModule;
    private final AppSetup appSetup;
    private final User user;

    public Api() {
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

        final BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        final String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);

        apiModule = new ApiModule(env);
        injector = Guice.createInjector(apiModule);
        offerBookService = injector.getInstance(OfferBookService.class);
        user = injector.getInstance(User.class);
        openOfferManager = injector.getInstance(OpenOfferManager.class);
        walletsManager = injector.getInstance(WalletsManager.class);
        walletsSetup = injector.getInstance(WalletsSetup.class);
        appSetup = injector.getInstance(AppSetupWithP2P.class);
        appSetup.start();

        try {

            injector.getInstance(BisqApiApplication.class).run("server", "bisq-api.yml");

            // All classes which are persisting objects need to be added here
            // Maintain order!
            ArrayList<PersistedDataHost> persistedDataHosts = new ArrayList<>();
            persistedDataHosts.add(injector.getInstance(Preferences.class));
            persistedDataHosts.add(injector.getInstance(User.class));
            persistedDataHosts.add(injector.getInstance(Navigation.class));
            persistedDataHosts.add(injector.getInstance(AddressEntryList.class));
            persistedDataHosts.add(injector.getInstance(TradeManager.class));
            persistedDataHosts.add(injector.getInstance(OpenOfferManager.class));
            persistedDataHosts.add(injector.getInstance(TradeManager.class));
            persistedDataHosts.add(injector.getInstance(ClosedTradableManager.class));
            persistedDataHosts.add(injector.getInstance(FailedTradesManager.class));
            persistedDataHosts.add(injector.getInstance(DisputeManager.class));
            persistedDataHosts.add(injector.getInstance(P2PService.class));
            persistedDataHosts.add(injector.getInstance(VotingManager.class));
            persistedDataHosts.add(injector.getInstance(CompensationRequestManager.class));

            // we apply at startup the reading of persisted data but don't want to get it triggered in the constructor
            persistedDataHosts.stream().forEach(e -> {
                try {
                    log.debug("call readPersisted at " + e.getClass().getSimpleName());
                    e.readPersisted();
                } catch (Throwable e1) {
                    log.error("readPersisted error", e1);
                }
            });

            ObjectProperty<Throwable> walletServiceException = new SimpleObjectProperty<>();

            // copy encryption handling from MainViewModel - initWalletService()
            walletsSetup.initialize(null,
                    () -> {
                        if (walletsManager.areWalletsEncrypted()) {
                            log.error("Encrypted wallets are not yet supported in the headless api.");
                        } else {
                            log.info("walletsSetup completed");
                        }
                    },
                    throwable -> log.error(throwable.toString()));

            Version.setBaseCryptoNetworkId(injector.getInstance(BisqEnvironment.class).getBaseCurrencyNetwork().ordinal());
            Version.printVersion();

        } catch (
                Exception e)

        {
            e.printStackTrace();
        }

    }

    public static void setEnvironment(Environment env) {
        Api.env = env;
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
