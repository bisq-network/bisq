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

package io.bisq.api.app;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.CommonSetup;
import bisq.common.storage.Storage;
import bisq.common.util.Profiler;
import bisq.common.util.Utilities;
import bisq.core.alert.AlertManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.wallet.*;
import bisq.core.dao.DaoSetup;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.core.trade.TradeManager;
import bisq.desktop.SystemTray;
import bisq.desktop.app.BisqApp;
import bisq.desktop.app.BisqAppModule;
import bisq.desktop.common.UITimer;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.guice.InjectorViewFactory;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.debug.DebugView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.*;
import bisq.desktop.setup.DesktopPersistedDataHost;
import bisq.desktop.util.ImageUtil;
import bisq.network.p2p.P2PService;
import ch.qos.logback.classic.Logger;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.bisq.api.service.BisqApiApplication;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.reactfx.EventStreams;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.Layout.INITIAL_SCENE_HEIGHT;
import static bisq.desktop.util.Layout.INITIAL_SCENE_WIDTH;

public class BisqApiWithUI extends Application {
    private static final Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BisqApiWithUI.class);

    private static final long LOG_MEMORY_PERIOD_MIN = 10;

    private static BisqEnvironment bisqEnvironment;
    public static Runnable shutDownHandler;
    private static Stage primaryStage;

    protected static void setEnvironment(BisqEnvironment bisqEnvironment) {
        BisqApiWithUI.bisqEnvironment = bisqEnvironment;
    }

    private BisqAppModule bisqAppModule;
    private Injector injector;
    private boolean popupOpened;
    private Scene scene;
    private final List<String> corruptedDatabaseFiles = new ArrayList<>();
    private boolean shutDownRequested;

    // NOTE: This method is not called on the JavaFX Application Thread.
    @Override
    public void init() {
        UserThread.setExecutor(Platform::runLater);
        UserThread.setTimerClass(UITimer.class);

        shutDownHandler = this::stop;
        BisqApp.shutDownHandler = this::stop;
        CommonSetup.setup(this::showErrorPopup);
        CoreSetup.setup(bisqEnvironment);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public void start(Stage stage) {
        BisqApiWithUI.primaryStage = stage;

        try {
            bisqAppModule = new BisqAppModule(bisqEnvironment, primaryStage);
            injector = Guice.createInjector(bisqAppModule);
            injector.getInstance(InjectorViewFactory.class).setInjector(injector);

            PersistedDataHost.apply(CorePersistedDataHost.getPersistedDataHosts(injector));
            PersistedDataHost.apply(DesktopPersistedDataHost.getPersistedDataHosts(injector));

            injector.getInstance(BisqApiApplication.class).run("server", "bisq-api.yml");

            DevEnv.setup(injector);

            MainView mainView = loadMainView(injector);
            scene = createAndConfigScene(mainView, injector);
            setupStage(scene);

            setDatabaseCorruptionHandler(mainView);

            checkForCorrectOSArchitecture();

            UserThread.runPeriodically(() -> Profiler.printSystemLoad(log), LOG_MEMORY_PERIOD_MIN, TimeUnit.MINUTES);
        } catch (Throwable throwable) {
            log.error("Error during app init", throwable);
            showErrorPopup(throwable, false);
        }
    }

    private Scene createAndConfigScene(MainView mainView, Injector injector) {
        Scene scene = new Scene(mainView.getRoot(), INITIAL_SCENE_WIDTH, INITIAL_SCENE_HEIGHT);
        scene.getStylesheets().setAll(
                "/bisq/desktop/bisq.css",
                "/bisq/desktop/images.css",
                "/bisq/desktop/CandleStickChart.css");
        addSceneKeyEventHandler(scene, injector);
        return scene;
    }

    private void setupStage(Scene scene) {
        // configure the system tray
        SystemTray.create(primaryStage, shutDownHandler);

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            stop();
        });


        // configure the primary stage
        primaryStage.setTitle(bisqEnvironment.getRequiredProperty(AppOptionKeys.APP_NAME_KEY));
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1020);
        primaryStage.setMinHeight(620);

        // on windows the title icon is also used as task bar icon in a larger size
        // on Linux no title icon is supported but also a large task bar icon is derived from that title icon
        String iconPath;
        if (Utilities.isOSX())
            iconPath = ImageUtil.isRetina() ? "/images/window_icon@2x.png" : "/images/window_icon.png";
        else if (Utilities.isWindows())
            iconPath = "/images/task_bar_icon_windows.png";
        else
            iconPath = "/images/task_bar_icon_linux.png";

        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream(iconPath)));

        // make the UI visible
        primaryStage.show();
    }

    private MainView loadMainView(Injector injector) {
        CachingViewLoader viewLoader = injector.getInstance(CachingViewLoader.class);
        return (MainView) viewLoader.load(MainView.class);
    }

    private void setDatabaseCorruptionHandler(MainView mainView) {
        Storage.setDatabaseCorruptionHandler((String fileName) -> {
            corruptedDatabaseFiles.add(fileName);
            if (mainView != null)
                mainView.setPersistedFilesCorrupted(corruptedDatabaseFiles);
        });
        mainView.setPersistedFilesCorrupted(corruptedDatabaseFiles);
    }


    private void addSceneKeyEventHandler(Scene scene, Injector injector) {
        scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
            Utilities.isAltOrCtrlPressed(KeyCode.W, keyEvent);
            if (Utilities.isCtrlPressed(KeyCode.W, keyEvent) ||
                    Utilities.isCtrlPressed(KeyCode.Q, keyEvent)) {
                stop();
            } else {
                if (Utilities.isAltOrCtrlPressed(KeyCode.E, keyEvent)) {
                    showEmptyWalletPopup(injector.getInstance(BtcWalletService.class), injector);
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.M, keyEvent)) {
                    showSendAlertMessagePopup(injector);
                } else if (Utilities.isAltOrCtrlPressed(KeyCode.F, keyEvent)) {
                    showFilterPopup(injector);
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
                    if (Utilities.isAltOrCtrlPressed(KeyCode.B, keyEvent)) {
                        // BSQ empty wallet not public yet
                        showEmptyWalletPopup(injector.getInstance(BsqWalletService.class), injector);
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.P, keyEvent)) {
                        showFPSWindow(scene);
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.Z, keyEvent)) {
                        showDebugWindow(scene, injector);
                    }
                }
            }
        });
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

    private void showEmptyWalletPopup(WalletService walletService, Injector injector) {
        EmptyWalletWindow emptyWalletWindow = injector.getInstance(EmptyWalletWindow.class);
        emptyWalletWindow.setWalletService(walletService);
        emptyWalletWindow.show();
    }

    private void showErrorPopup(Throwable throwable, boolean doShutDown) {
        if (!shutDownRequested) {
            if (scene == null) {
                log.warn("Scene not available yet, we create a new scene. The bug might be caused by an exception in a constructor or by a circular dependency in guice. throwable=" + throwable.toString());
                scene = new Scene(new StackPane(), 1000, 650);
                scene.getStylesheets().setAll(
                        "/bisq/desktop/bisq.css",
                        "/bisq/desktop/images.css");
                primaryStage.setScene(scene);
                primaryStage.show();
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
        stage.setX(primaryStage.getX() + primaryStage.getWidth() + 10);
        stage.setY(primaryStage.getY());
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
        stage.setX(primaryStage.getX() + primaryStage.getWidth() + 10);
        stage.setY(primaryStage.getY());
        stage.setWidth(200);
        stage.setHeight(100);
        stage.show();
    }

    private void checkForCorrectOSArchitecture() {
        if (!Utilities.isCorrectOSArchitecture()) {
            String osArchitecture = Utilities.getOSArchitecture();
            // We don't force a shutdown as the osArchitecture might in strange cases return a wrong value.
            // Needs at least more testing on different machines...
            new Popup<>().warning(Res.get("popup.warning.wrongVersion",
                    osArchitecture,
                    Utilities.getJVMArchitecture(),
                    osArchitecture))
                    .show();
        }
    }

    @SuppressWarnings("CodeBlock2Expr")
    @Override
    public void stop() {
        if (!shutDownRequested) {
            new Popup<>().headLine(Res.get("popup.shutDownInProgress.headline"))
                    .backgroundInfo(Res.get("popup.shutDownInProgress.msg"))
                    .hideCloseButton()
                    .useAnimation(false)
                    .show();
            //noinspection CodeBlock2Expr
            UserThread.runAfter(() -> {
                gracefulShutDown(() -> {
                    log.debug("App shutdown complete");
                    System.exit(0);
                });
            }, 200, TimeUnit.MILLISECONDS);
            shutDownRequested = true;
        }
    }

    private void gracefulShutDown(ResultHandler resultHandler) {
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(TradeManager.class).shutDown();
                injector.getInstance(DaoSetup.class).shutDown();
                //noinspection CodeBlock2Expr
                injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                            bisqAppModule.close(injector);
                            log.debug("Graceful shutdown completed");
                            resultHandler.handleResult();
                        });
                        injector.getInstance(WalletsSetup.class).shutDown();
                        injector.getInstance(BtcWalletService.class).shutDown();
                        injector.getInstance(BsqWalletService.class).shutDown();
                    });
                });
                // we wait max 20 sec.
                UserThread.runAfter(() -> {
                    log.warn("Timeout triggered resultHandler");
                    resultHandler.handleResult();
                }, 20);
            } else {
                log.warn("injector == null triggered resultHandler");
                UserThread.runAfter(resultHandler::handleResult, 1);
            }
        } catch (Throwable t) {
            log.error("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
