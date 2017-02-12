/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.alert.AlertManager;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.CommonOptionKeys;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.LimitedKeyStrengthException;
import io.bitsquare.common.util.Profiler;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.filter.FilterManager;
import io.bitsquare.gui.SystemTray;
import io.bitsquare.gui.common.UITimer;
import io.bitsquare.gui.common.view.CachingViewLoader;
import io.bitsquare.gui.common.view.View;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.common.view.guice.InjectorViewFactory;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.MainViewModel;
import io.bitsquare.gui.main.debug.DebugView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.*;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.OpenOfferManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bitcoinj.store.BlockStoreException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.controlsfx.dialog.Dialogs;
import org.reactfx.EventStreams;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.app.AppOptionKeys.APP_NAME_KEY;

public class BitsquareApp extends Application {
    private static final Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BitsquareApp.class);

    private static final long LOG_MEMORY_PERIOD_MIN = 10;

    private static Environment env;

    private BitsquareAppModule bitsquareAppModule;
    private Injector injector;
    private boolean popupOpened;

    private static Stage primaryStage;
    private Scene scene;
    private final List<String> corruptedDatabaseFiles = new ArrayList<>();
    private MainView mainView;

    public static Runnable shutDownHandler;
    private boolean shutDownRequested;

    public static void setEnvironment(Environment env) {
        BitsquareApp.env = env;
    }

    @Override
    public void start(Stage stage) throws IOException {
        BitsquareApp.primaryStage = stage;

        String logPath = Paths.get(env.getProperty(AppOptionKeys.APP_DATA_DIR_KEY), "bitsquare").toString();
        Log.setup(logPath);
        log.info("Log files under: " + logPath);
        Version.printVersion();
        Utilities.printSysInfo();
        Log.setLevel(Level.toLevel(env.getRequiredProperty(CommonOptionKeys.LOG_LEVEL_KEY)));

        UserThread.setExecutor(Platform::runLater);
        UserThread.setTimerClass(UITimer.class);

        shutDownHandler = this::stop;

        // setup UncaughtExceptionHandler
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            // Might come from another thread 
            if (throwable.getCause() != null && throwable.getCause().getCause() != null &&
                    throwable.getCause().getCause() instanceof BlockStoreException) {
                log.error(throwable.getMessage());
            } else if (throwable instanceof ClassCastException &&
                    "sun.awt.image.BufImgSurfaceData cannot be cast to sun.java2d.xr.XRSurfaceData".equals(throwable.getMessage())) {
                log.warn(throwable.getMessage());
            } else {
                log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
                log.error("throwableMessage= " + throwable.getMessage());
                log.error("throwableClass= " + throwable.getClass());
                log.error("Stack trace:\n" + ExceptionUtils.getStackTrace(throwable));
                throwable.printStackTrace();
                UserThread.execute(() -> showErrorPopup(throwable, false));
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);

        try {
            Utilities.checkCryptoPolicySetup();
        } catch (NoSuchAlgorithmException | LimitedKeyStrengthException e) {
            e.printStackTrace();
            UserThread.execute(() -> showErrorPopup(e, true));
        }

        Security.addProvider(new BouncyCastleProvider());

        try {
            // Guice
            bitsquareAppModule = new BitsquareAppModule(env, primaryStage);
            injector = Guice.createInjector(bitsquareAppModule);
            injector.getInstance(InjectorViewFactory.class).setInjector(injector);

            Version.setBtcNetworkId(injector.getInstance(BitsquareEnvironment.class).getBitcoinNetwork().ordinal());

            if (Utilities.isLinux())
                System.setProperty("prism.lcdtext", "false");

            Storage.setDatabaseCorruptionHandler((String fileName) -> {
                corruptedDatabaseFiles.add(fileName);
                if (mainView != null)
                    mainView.setPersistedFilesCorrupted(corruptedDatabaseFiles);
            });

            // load the main view and create the main scene
            CachingViewLoader viewLoader = injector.getInstance(CachingViewLoader.class);
            mainView = (MainView) viewLoader.load(MainView.class);
            mainView.setPersistedFilesCorrupted(corruptedDatabaseFiles);

           /* Storage.setDatabaseCorruptionHandler((String fileName) -> {
                corruptedDatabaseFiles.add(fileName);
                if (mainView != null)
                    mainView.setPersistedFilesCorrupted(corruptedDatabaseFiles);
            });*/

            scene = new Scene(mainView.getRoot(), 1200, 700); //740

            Font.loadFont(getClass().getResource("/fonts/Verdana.ttf").toExternalForm(), 13);
            Font.loadFont(getClass().getResource("/fonts/VerdanaBold.ttf").toExternalForm(), 13);
            Font.loadFont(getClass().getResource("/fonts/VerdanaItalic.ttf").toExternalForm(), 13);
            Font.loadFont(getClass().getResource("/fonts/VerdanaBoldItalic.ttf").toExternalForm(), 13);
            scene.getStylesheets().setAll(
                    "/io/bitsquare/gui/bitsquare.css",
                    "/io/bitsquare/gui/images.css",
                    "/io/bitsquare/gui/CandleStickChart.css");

            // configure the system tray
            SystemTray.create(primaryStage, shutDownHandler);

            primaryStage.setOnCloseRequest(event -> {
                event.consume();
                stop();
            });
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
                if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN).match(keyEvent) || new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN).match(keyEvent)) {
                    stop();
                } else if (new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN).match(keyEvent) || new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN).match(keyEvent)) {
                    stop();
                } else if (new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN).match(keyEvent) || new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN).match(keyEvent)) {
                    showEmptyWalletPopup();
                } else if (new KeyCodeCombination(KeyCode.M, KeyCombination.ALT_DOWN).match(keyEvent)) {
                    showSendAlertMessagePopup();
                } else if (new KeyCodeCombination(KeyCode.F, KeyCombination.ALT_DOWN).match(keyEvent)) {
                    showFilterPopup();
                } else if (new KeyCodeCombination(KeyCode.F, KeyCombination.ALT_DOWN).match(keyEvent)) {
                    showFPSWindow();
                } else if (new KeyCodeCombination(KeyCode.J, KeyCombination.ALT_DOWN).match(keyEvent)) {
                    WalletService walletService = injector.getInstance(WalletService.class);
                    if (walletService.getWallet() != null)
                        new ShowWalletDataWindow(walletService).information("Wallet raw data").show();
                    else
                        new Popup<>().warning("The wallet is not initialized yet").show();
                } else if (new KeyCodeCombination(KeyCode.G, KeyCombination.ALT_DOWN).match(keyEvent)) {
                    TradeWalletService tradeWalletService = injector.getInstance(TradeWalletService.class);
                    WalletService walletService = injector.getInstance(WalletService.class);
                    if (walletService.getWallet() != null)
                        new SpendFromDepositTxWindow(tradeWalletService).information("Emergency wallet tool").show();
                    else
                        new Popup<>().warning("The wallet is not initialized yet").show();
                } else if (DevFlags.DEV_MODE && new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN).match(keyEvent)) {
                    showDebugWindow();
                }
            });

            // configure the primary stage
            primaryStage.setTitle(env.getRequiredProperty(APP_NAME_KEY));
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1000); // 1190
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


            if (!Utilities.isCorrectOSArchitecture()) {
                String osArchitecture = Utilities.getOSArchitecture();
                // We don't force a shutdown as the osArchitecture might in strange cases return a wrong value.
                // Needs at least more testing on different machines...
                new Popup<>().warning("You probably have the wrong Bitsquare version for this computer.\n" +
                        "Your computer's architecture is: " + osArchitecture + ".\n" +
                        "The Bitsquare binary you installed is: " + Utilities.getJVMArchitecture() + ".\n" +
                        "Please shut down and re-install the correct version (" + osArchitecture + ").")
                        .show();
            }

            UserThread.runPeriodically(() -> Profiler.printSystemLoad(log), LOG_MEMORY_PERIOD_MIN, TimeUnit.MINUTES);

        } catch (
                Throwable throwable
                )

        {
            showErrorPopup(throwable, false);
        }

    }

    private void showSendAlertMessagePopup() {
        AlertManager alertManager = injector.getInstance(AlertManager.class);
        new SendAlertMessageWindow()
                .onAddAlertMessage(alertManager::addAlertMessageIfKeyIsValid)
                .onRemoveAlertMessage(alertManager::removeAlertMessageIfKeyIsValid)
                .show();
    }

    private void showFilterPopup() {
        FilterManager filterManager = injector.getInstance(FilterManager.class);
        new FilterWindow(filterManager)
                .onAddFilter(filterManager::addFilterMessageIfKeyIsValid)
                .onRemoveFilter(filterManager::removeFilterMessageIfKeyIsValid)
                .show();
    }

    private void showEmptyWalletPopup() {
        injector.getInstance(EmptyWalletWindow.class).show();
    }

    private void showErrorPopup(Throwable throwable, boolean doShutDown) {
        if (!shutDownRequested) {
            if (scene == null) {
                log.warn("Scene not available yet, we create a new scene. The bug might be caused by an exception in a constructor or by a circular dependency in guice.");
                scene = new Scene(new StackPane(), 1000, 650);
                scene.getStylesheets().setAll(
                        "/io/bitsquare/gui/bitsquare.css",
                        "/io/bitsquare/gui/images.css");
                primaryStage.setScene(scene);
                primaryStage.show();
            }
            try {
                try {
                    if (!popupOpened) {
                        String message = throwable.getMessage();
                        popupOpened = true;
                        if (message != null)
                            new Popup().error(message).onClose(() -> popupOpened = false).show();
                        else
                            new Popup().error(throwable.toString()).onClose(() -> popupOpened = false).show();
                    }
                } catch (Throwable throwable3) {
                    log.error("Error at displaying Throwable.");
                    throwable3.printStackTrace();
                }
                if (doShutDown)
                    stop();
            } catch (Throwable throwable2) {
                // If printStackTrace cause a further exception we don't pass the throwable to the Popup.
                Dialogs.create()
                        .owner(primaryStage)
                        .title("Error")
                        .message(throwable.toString())
                        .masthead("A fatal exception occurred at startup.")
                        .showError();
                if (doShutDown)
                    stop();
            }
        }
    }

    // Used for debugging trade process
    private void showDebugWindow() {
        ViewLoader viewLoader = injector.getInstance(ViewLoader.class);
        View debugView = viewLoader.load(DebugView.class);
        Parent parent = (Parent) debugView.getRoot();
        Stage stage = new Stage();
        stage.setScene(new Scene(parent));
        stage.setTitle("Debug window");
        stage.initModality(Modality.NONE);
        stage.initStyle(StageStyle.UTILITY);
        stage.initOwner(scene.getWindow());
        stage.setX(primaryStage.getX() + primaryStage.getWidth() + 10);
        stage.setY(primaryStage.getY());
        stage.show();
    }


    private void showFPSWindow() {
        Label label = new Label();
        EventStreams.animationTicks()
                .latestN(100)
                .map(ticks -> {
                    int n = ticks.size() - 1;
                    return n * 1_000_000_000.0 / (ticks.get(n) - ticks.get(0));
                })
                .map(d -> String.format("FPS: %.3f", d))
                .feedTo(label.textProperty());

        Pane root = new StackPane();
        root.getChildren().add(label);
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle("FPS");
        stage.initModality(Modality.NONE);
        stage.initStyle(StageStyle.UTILITY);
        stage.initOwner(scene.getWindow());
        stage.setX(primaryStage.getX() + primaryStage.getWidth() + 10);
        stage.setY(primaryStage.getY());
        stage.setWidth(200);
        stage.setHeight(100);
        stage.show();
    }

    @Override
    public void stop() {
        if (!shutDownRequested) {
            new Popup().headLine("Shut down in progress")
                    .backgroundInfo("Shutting down application can take a few seconds.\n" +
                            "Please don't interrupt this process.")
                    .hideCloseButton()
                    .useAnimation(false)
                    .show();
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
        log.debug("gracefulShutDown");
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(MainViewModel.class).shutDown();
                injector.getInstance(TradeManager.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        injector.getInstance(WalletService.class).shutDownDone.addListener((ov, o, n) -> {
                            bitsquareAppModule.close(injector);
                            log.debug("Graceful shutdown completed");
                            resultHandler.handleResult();
                        });
                        injector.getInstance(WalletService.class).shutDown();
                    });
                });
                // we wait max 20 sec.
                UserThread.runAfter(resultHandler::handleResult, 20);
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
