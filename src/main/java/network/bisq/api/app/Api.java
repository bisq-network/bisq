package network.bisq.api.app;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.CommonSetup;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.network.p2p.P2PService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import network.bisq.api.service.BisqApiApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class Api {
    private static final Logger log = LoggerFactory.getLogger(Api.class);
    private static ApiEnvironment env;
    private final Injector injector;
    MainViewModelHeadless mainViewModelHeadless;

    public Api() {
        CommonSetup.setup((throwable1, doShutdown) -> {
            if (doShutdown) {
                shutDown();
            }
        });
        CoreSetup.setup(env);


        ApiHeadlessModule apiHeadlessModule = new ApiHeadlessModule(env);
        injector = Guice.createInjector(apiHeadlessModule);
        PriceFeedService priceFeedService = injector.getInstance(PriceFeedService.class);
        mainViewModelHeadless = injector.getInstance(MainViewModelHeadless.class);

        //appSetup.start(); // TODO refactor MainViewModel into AppSetupWithP2P and use it for API + GUI
        mainViewModelHeadless.start();

        try {
            PersistedDataHost.apply(CorePersistedDataHost.getPersistedDataHosts(injector));

            final BisqApiApplication bisqApiApplication = injector.getInstance(BisqApiApplication.class);
            bisqApiApplication.setShutdown(this::shutDown);
            bisqApiApplication.run("server", "bisq-api.yml");
            /*
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
                    */
            long ts = new Date().getTime();
            boolean logged[] = {false};
            priceFeedService.setCurrencyCodeOnInit();
            priceFeedService.requestPriceFeed(price -> {
                        if (!logged[0]) {
                            log.info("We received data from the price relay after {} ms.",
                                    (new Date().getTime() - ts));
                            logged[0] = true;
                        }
                    },
                    (errorMessage, throwable) -> log.error("requestPriceFeed failed:" + errorMessage));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void setEnvironment(ApiEnvironment env) {
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
                final Runnable shutdownBtcWalletService = () -> injector.getInstance(BtcWalletService.class).shutDown();
                final Runnable shutdownP2PService = () -> injector.getInstance(P2PService.class).shutDown(shutdownBtcWalletService);
                injector.getInstance(OpenOfferManager.class).shutDown(shutdownP2PService);
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
