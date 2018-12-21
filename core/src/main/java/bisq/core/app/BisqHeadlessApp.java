/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app;

import bisq.core.trade.TradeManager;

import bisq.common.UserThread;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.storage.CorruptedDatabaseFilesHandler;
import bisq.common.util.Profiler;

import com.google.inject.Injector;

import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BisqHeadlessApp implements HeadlessApp {
    private static final long LOG_MEMORY_PERIOD_MIN = 10;
    @Getter
    private static Runnable shutDownHandler;

    @Setter
    protected Injector injector;
    @Setter
    private GracefulShutDownHandler gracefulShutDownHandler;
    private boolean shutDownRequested;
    private BisqSetup bisqSetup;
    private CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler;
    private TradeManager tradeManager;

    public BisqHeadlessApp() {
        shutDownHandler = this::stop;
    }

    public void startApplication() {
        try {
            bisqSetup = injector.getInstance(BisqSetup.class);

            corruptedDatabaseFilesHandler = injector.getInstance(CorruptedDatabaseFilesHandler.class);
            tradeManager = injector.getInstance(TradeManager.class);

            setupHandlers();

            UserThread.runPeriodically(() -> Profiler.printSystemLoad(log), LOG_MEMORY_PERIOD_MIN, TimeUnit.MINUTES);
        } catch (Throwable throwable) {
            log.error("Error during app init", throwable);
            handleUncaughtException(throwable, false);
        }
    }

    @Override
    public void onSetupComplete() {
        log.info("onSetupComplete");
    }

    protected void setupHandlers() {
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
        bisqSetup.setDaoErrorMessageHandler(errorMessage -> log.info("onDaoErrorMessageHandler. errorMessage={}", errorMessage));
        bisqSetup.setDaoWarnMessageHandler(warnMessage -> log.info("onDaoWarnMessageHandler. warnMessage={}", warnMessage));
        bisqSetup.setDisplaySecurityRecommendationHandler(key -> log.info("onDisplaySecurityRecommendationHandler"));
        bisqSetup.setDisplayLocalhostHandler(key -> log.info("onDisplayLocalhostHandler"));
        bisqSetup.setWrongOSArchitectureHandler(msg -> log.info("onWrongOSArchitectureHandler. msg={}", msg));
        bisqSetup.setVoteResultExceptionHandler(voteResultException -> log.info("voteResultException={}", voteResultException));

        //TODO move to bisqSetup
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
