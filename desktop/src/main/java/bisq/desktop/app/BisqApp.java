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

package bisq.desktop.app;

import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.debug.DebugView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.EmptyWalletWindow;
import bisq.desktop.main.overlays.windows.FilterWindow;
import bisq.desktop.main.overlays.windows.ManualPayoutTxWindow;
import bisq.desktop.main.overlays.windows.SendAlertMessageWindow;
import bisq.desktop.main.overlays.windows.ShowWalletDataWindow;
import bisq.desktop.util.ImageUtil;

import bisq.core.alert.AlertManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.app.AvoidStandbyModeService;
import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.governance.voteresult.MissingDataRequestService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.user.Preferences;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.app.Log;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.setup.UncaughtExceptionHandler;
import bisq.common.util.Profiler;
import bisq.common.util.Utilities;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import org.reactfx.EventStreams;

import javafx.application.Application;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.Layout.INITIAL_WINDOW_HEIGHT;
import static bisq.desktop.util.Layout.INITIAL_WINDOW_WIDTH;
import static bisq.desktop.util.Layout.MIN_WINDOW_HEIGHT;
import static bisq.desktop.util.Layout.MIN_WINDOW_WIDTH;

@Slf4j
public class BisqApp extends Application implements UncaughtExceptionHandler {
    private static final long LOG_MEMORY_PERIOD_MIN = 10;
    @Setter
    private static Consumer<Application> appLaunchedHandler;
    @Getter
    private static Runnable shutDownHandler;

    @Setter
    private Injector injector;
    @Setter
    private GracefulShutDownHandler gracefulShutDownHandler;
    private Stage stage;
    private boolean popupOpened;
    private Scene scene;
    private boolean shutDownRequested;

    public BisqApp() {
        shutDownHandler = this::stop;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // JavaFx Application implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // NOTE: This method is not called on the JavaFX Application Thread.
    @Override
    public void init() {
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        appLaunchedHandler.accept(this);
    }

    public void startApplication(Runnable onUiReadyHandler) {
        try {
            MainView mainView = loadMainView(injector);
            mainView.setOnUiReadyHandler(onUiReadyHandler);
            scene = createAndConfigScene(mainView, injector);
            setupStage(scene);

            injector.getInstance(AvoidStandbyModeService.class).init();

            UserThread.runPeriodically(() -> Profiler.printSystemLoad(log), LOG_MEMORY_PERIOD_MIN, TimeUnit.MINUTES);
        } catch (Throwable throwable) {
            log.error("Error during app init", throwable);
            handleUncaughtException(throwable, false);
        }
    }

    @Override
    public void stop() {
        if (!shutDownRequested) {
            new Popup<>().headLine(Res.get("popup.shutDownInProgress.headline"))
                    .backgroundInfo(Res.get("popup.shutDownInProgress.msg"))
                    .hideCloseButton()
                    .useAnimation(false)
                    .show();
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
            if (scene == null) {
                log.warn("Scene not available yet, we create a new scene. The bug might be caused by an exception in a constructor or by a circular dependency in Guice. throwable=" + throwable.toString());
                scene = new Scene(new StackPane(), 1000, 650);
                scene.getStylesheets().setAll(
                        "/bisq/desktop/bisq.css",
                        "/bisq/desktop/images.css");
                stage.setScene(scene);
                stage.show();
            }
            try {
                try {
                    if (!popupOpened) {
                        String message = throwable.getMessage();
                        popupOpened = true;
                        if (message != null)
                            new Popup<>().error(message).onClose(() -> popupOpened = false).show();
                        else
                            new Popup<>().error(throwable.toString()).onClose(() -> popupOpened = false).show();
                    }
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Scene createAndConfigScene(MainView mainView, Injector injector) {
        Rectangle maxWindowBounds = new Rectangle();
        try {
            maxWindowBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        } catch (IllegalArgumentException e) {
            // Multi-screen environments may encounter IllegalArgumentException (Window must not be zero)
            // Just ignore the exception and continue, which means the window will use the minimum window size below
            // since we are unable to determine if we can use a larger size
        }
        Scene scene = new Scene(mainView.getRoot(),
                maxWindowBounds.width < INITIAL_WINDOW_WIDTH ?
                        (maxWindowBounds.width < MIN_WINDOW_WIDTH ? MIN_WINDOW_WIDTH : maxWindowBounds.width) :
                        INITIAL_WINDOW_WIDTH,
                maxWindowBounds.height < INITIAL_WINDOW_HEIGHT ?
                        (maxWindowBounds.height < MIN_WINDOW_HEIGHT ? MIN_WINDOW_HEIGHT : maxWindowBounds.height) :
                        INITIAL_WINDOW_HEIGHT);
        scene.getStylesheets().setAll(
                "/bisq/desktop/bisq.css",
                "/bisq/desktop/images.css",
                "/bisq/desktop/CandleStickChart.css");
        addSceneKeyEventHandler(scene, injector);
        return scene;
    }

    private void setupStage(Scene scene) {
        // configure the system tray
        SystemTray.create(stage, shutDownHandler);

        stage.setOnCloseRequest(event -> {
            event.consume();
            shutDownByUser();
        });

        // configure the primary stage
        String appName = injector.getInstance(Key.get(String.class, Names.named(AppOptionKeys.APP_NAME_KEY)));
        if (!BisqEnvironment.getBaseCurrencyNetwork().isMainnet())
            appName += " [" + Res.get(BisqEnvironment.getBaseCurrencyNetwork().name()) + "]";

        stage.setTitle(appName);
        stage.setScene(scene);
        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);

        // on Windows the title icon is also used as task bar icon in a larger size
        // on Linux no title icon is supported but also a large task bar icon is derived from that title icon
        String iconPath;
        if (Utilities.isOSX())
            iconPath = ImageUtil.isRetina() ? "/images/window_icon@2x.png" : "/images/window_icon.png";
        else if (Utilities.isWindows())
            iconPath = "/images/task_bar_icon_windows.png";
        else
            iconPath = "/images/task_bar_icon_linux.png";

        stage.getIcons().add(new Image(getClass().getResourceAsStream(iconPath)));

        // make the UI visible
        stage.show();
    }

    private MainView loadMainView(Injector injector) {
        CachingViewLoader viewLoader = injector.getInstance(CachingViewLoader.class);
        return (MainView) viewLoader.load(MainView.class);
    }

    private void addSceneKeyEventHandler(Scene scene, Injector injector) {
        scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
            if (Utilities.isCtrlPressed(KeyCode.W, keyEvent) ||
                    Utilities.isCtrlPressed(KeyCode.Q, keyEvent)) {
                shutDownByUser();
            } else {
                if (Utilities.isAltOrCtrlPressed(KeyCode.E, keyEvent)) {
                    showBtcEmergencyWalletPopup(injector);
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.B, keyEvent)) {
                    showBsqEmergencyWalletPopup(injector);
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.M, keyEvent)) {
                    showSendAlertMessagePopup(injector);
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.F, keyEvent)) {
                    showFilterPopup(injector);
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.UP, keyEvent)) {
                    log.warn("We re-published all proposalPayloads and blindVotePayloads to the P2P network.");
                    injector.getInstance(MissingDataRequestService.class).reRepublishAllGovernanceData();
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.T, keyEvent)) {
                    // Toggle between show tor logs and only show warnings. Helpful in case of connection problems
                    String pattern = "org.berndpruenster.netlayer";
                    Level logLevel = ((Logger) LoggerFactory.getLogger(pattern)).getLevel();
                    if (logLevel != Level.DEBUG) {
                        log.info("Set log level for org.berndpruenster.netlayer classes to DEBUG");
                        Log.setCustomLogLevel(pattern, Level.DEBUG);
                    } else {
                        log.info("Set log level for org.berndpruenster.netlayer classes to WARN");
                        Log.setCustomLogLevel(pattern, Level.WARN);
                    }
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.J, keyEvent)) {
                    WalletsManager walletsManager = injector.getInstance(WalletsManager.class);
                    if (walletsManager.areWalletsAvailable())
                        new ShowWalletDataWindow(walletsManager).show();
                    else
                        new Popup<>().warning(Res.get("popup.warning.walletNotInitialized")).show();
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.G, keyEvent)) {
                    if (injector.getInstance(BtcWalletService.class).isWalletReady())
                        injector.getInstance(ManualPayoutTxWindow.class).show();
                    else
                        new Popup<>().warning(Res.get("popup.warning.walletNotInitialized")).show();
                } else if (DevEnv.isDevMode()) {
                    // dev ode only
                    if (Utilities.isAltOrCtrlPressed(KeyCode.P, keyEvent)) {
                        showFPSWindow(scene);
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.Z, keyEvent)) {
                        showDebugWindow(scene, injector);
                    }
                }
            }
        });
    }

    private void shutDownByUser() {
        boolean hasOpenOffers = false;
        for (OpenOffer openOffer : injector.getInstance(OpenOfferManager.class).getObservableList()) {
            if (openOffer.getState().equals(OpenOffer.State.AVAILABLE)) {
                hasOpenOffers = true;
                break;
            }
        }
        if (!hasOpenOffers) {
            // No open offers, so no need to show the popup.
            stop();
            return;
        }

        // We show a popup to inform user that open offers will be removed if Bisq is not running.
        String key = "showOpenOfferWarnPopupAtShutDown";
        if (injector.getInstance(Preferences.class).showAgain(key) && !DevEnv.isDevMode()) {
            new Popup<>().information(Res.get("popup.info.shutDownWithOpenOffers"))
                    .dontShowAgainId(key)
                    .useShutDownButton()
                    .closeButtonText(Res.get("shared.cancel"))
                    .show();
        } else {
            stop();
        }
    }

    private void showSendAlertMessagePopup(Injector injector) {
        AlertManager alertManager = injector.getInstance(AlertManager.class);
        boolean useDevPrivilegeKeys = injector.getInstance(Key.get(Boolean.class, Names.named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS)));
        new SendAlertMessageWindow(useDevPrivilegeKeys)
                .onAddAlertMessage(alertManager::addAlertMessageIfKeyIsValid)
                .onRemoveAlertMessage(alertManager::removeAlertMessageIfKeyIsValid)
                .show();
    }

    private void showFilterPopup(Injector injector) {
        FilterManager filterManager = injector.getInstance(FilterManager.class);
        boolean useDevPrivilegeKeys = injector.getInstance(Key.get(Boolean.class, Names.named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS)));
        new FilterWindow(filterManager, useDevPrivilegeKeys)
                .onAddFilter(filterManager::addFilterMessageIfKeyIsValid)
                .onRemoveFilter(filterManager::removeFilterMessageIfKeyIsValid)
                .show();
    }

    private void showBtcEmergencyWalletPopup(Injector injector) {
        EmptyWalletWindow emptyWalletWindow = injector.getInstance(EmptyWalletWindow.class);
        emptyWalletWindow.setIsBtc(true);
        emptyWalletWindow.show();
    }

    private void showBsqEmergencyWalletPopup(Injector injector) {
        EmptyWalletWindow emptyWalletWindow = injector.getInstance(EmptyWalletWindow.class);
        emptyWalletWindow.setIsBtc(false);
        emptyWalletWindow.show();
    }

    // Used for debugging trade process
    private void showDebugWindow(Scene scene, Injector injector) {
        ViewLoader viewLoader = injector.getInstance(ViewLoader.class);
        View debugView = viewLoader.load(DebugView.class);
        Parent parent = (Parent) debugView.getRoot();
        Stage stage = new Stage();
        stage.setScene(new Scene(parent));
        stage.setTitle("Debug window"); // Don't translate, just for dev
        stage.initModality(Modality.NONE);
        stage.initStyle(StageStyle.UTILITY);
        stage.initOwner(scene.getWindow());
        stage.setX(this.stage.getX() + this.stage.getWidth() + 10);
        stage.setY(this.stage.getY());
        stage.show();
    }

    private void showFPSWindow(Scene scene) {
        Label label = new AutoTooltipLabel();
        EventStreams.animationTicks()
                .latestN(100)
                .map(ticks -> {
                    int n = ticks.size() - 1;
                    return n * 1_000_000_000.0 / (ticks.get(n) - ticks.get(0));
                })
                .map(d -> String.format("FPS: %.3f", d)) // Don't translate, just for dev
                .feedTo(label.textProperty());

        Pane root = new StackPane();
        root.getChildren().add(label);
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle("FPS"); // Don't translate, just for dev
        stage.initModality(Modality.NONE);
        stage.initStyle(StageStyle.UTILITY);
        stage.initOwner(scene.getWindow());
        stage.setX(this.stage.getX() + this.stage.getWidth() + 10);
        stage.setY(this.stage.getY());
        stage.setWidth(200);
        stage.setHeight(100);
        stage.show();
    }
}
