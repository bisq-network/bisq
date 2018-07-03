package network.bisq.api.app;

import com.google.inject.Injector;

import java.util.concurrent.TimeUnit;

import bisq.common.UserThread;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.setup.UncaughtExceptionHandler;
import bisq.common.storage.CorruptedDatabaseFilesHandler;
import bisq.common.util.Profiler;
import bisq.core.app.BisqSetup;
import bisq.core.trade.TradeManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import network.bisq.api.service.BisqApiApplication;

@Slf4j
public class Api implements UncaughtExceptionHandler {
    private static final long LOG_MEMORY_PERIOD_MIN = 10;
    @Getter
    private static Runnable shutDownHandler;

    @Setter
    private Injector injector;
    @Setter
    private GracefulShutDownHandler gracefulShutDownHandler;
    private boolean shutDownRequested;
    private BisqSetup bisqSetup;
    private CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler;
    private TradeManager tradeManager;

    public Api() {
        shutDownHandler = this::stop;
    }


    public void startApplication() {
        try {
            bisqSetup = injector.getInstance(BisqSetup.class);
            corruptedDatabaseFilesHandler = injector.getInstance(CorruptedDatabaseFilesHandler.class);
            tradeManager = injector.getInstance(TradeManager.class);

            setupHandlers();
            bisqSetup.start(this::onSetupComplete);

            UserThread.runPeriodically(() -> Profiler.printSystemLoad(log), LOG_MEMORY_PERIOD_MIN, TimeUnit.MINUTES);
        } catch (Throwable throwable) {
            log.error("Error during app init", throwable);
            handleUncaughtException(throwable, false);
        }
    }

    private void onSetupComplete() {
        log.info("onSetupComplete");
        final BisqApiApplication bisqApiApplication = injector.getInstance(BisqApiApplication.class);
        bisqApiApplication.setShutdown(this::stop);
        try {
            bisqApiApplication.run("server", "bisq-api.yml");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setupHandlers() {
        bisqSetup.setDisplayTacHandler(acceptedHandler -> {
            log.info("onDisplayTacHandler: We accept the tacs automatically in headless mode");
            acceptedHandler.run();
        });
        bisqSetup.setCryptoSetupFailedHandler(msg -> log.info("onCryptoSetupFailedHandler: msg={}", msg));
        bisqSetup.setDisplayTorNetworkSettingsHandler(show -> log.info("onDisplayTorNetworkSettingsHandler: show={}", show));
        bisqSetup.setSpvFileCorruptedHandler(msg -> log.info("onSpvFileCorruptedHandler: msg={}", msg));
        bisqSetup.setChainFileLockedExceptionHandler(msg -> log.info("onChainFileLockedExceptionHandler: msg={}", msg));
        bisqSetup.setLockedUpFundsHandler(msg -> log.info("onLockedUpFundsHandler: msg={}", msg));
        bisqSetup.setShowFirstPopupIfResyncSPVRequestedHandler(() -> log.info("onShowFirstPopupIfResyncSPVRequestedHandler"));
        bisqSetup.setRequestWalletPasswordHandler(aesKeyHandler -> log.info("onRequestWalletPasswordHandler"));
        bisqSetup.setDisplayUpdateHandler((alert, key) -> log.info("onDisplayUpdateHandler"));
        bisqSetup.setDisplayAlertHandler(alert -> log.info("onDisplayAlertHandler. alert={}", alert));
        bisqSetup.setDisplayPrivateNotificationHandler(privateNotification -> log.info("onDisplayPrivateNotificationHandler. privateNotification={}", privateNotification));
        bisqSetup.setDaoSetupErrorHandler(errorMessage -> log.info("onDaoSetupErrorHandler. errorMessage={}", errorMessage));
        bisqSetup.setDisplaySecurityRecommendationHandler(key -> log.info("onDisplaySecurityRecommendationHandler"));
        bisqSetup.setWrongOSArchitectureHandler(msg -> log.info("onWrongOSArchitectureHandler. msg={}", msg));

        corruptedDatabaseFilesHandler.getCorruptedDatabaseFiles().ifPresent(files -> log.info("getCorruptedDatabaseFiles. files={}", files));
        tradeManager.setTakeOfferRequestErrorMessageHandler(errorMessage -> log.info("onTakeOfferRequestErrorMessageHandler"));
    }

    public void stop() {
        if (!shutDownRequested) {
            UserThread.runAfter(() -> {
                gracefulShutDownHandler.gracefulShutDown(() -> {
                    log.debug("App shutdown complete");
                });
            }, 200, TimeUnit.MILLISECONDS);
            shutDownRequested = true;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UncaughtExceptionHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        if (!shutDownRequested) {
            try {
                try {
                    log.error(throwable.getMessage());
                } catch (Throwable throwable3) {
                    log.error("Error at displaying Throwable.");
                    throwable3.printStackTrace();
                }
                if (doShutDown)
                    stop();
            } catch (Throwable throwable2) {
                // If printStackTrace cause a further exception we don't pass the throwable to the Popup.
                log.error(throwable2.toString());
                if (doShutDown)
                    stop();
            }
        }
    }
}
