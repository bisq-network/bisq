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

import ch.qos.logback.classic.Logger;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.alert.AlertManager;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.SystemTray;
import io.bitsquare.gui.common.view.CachingViewLoader;
import io.bitsquare.gui.common.view.View;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.common.view.guice.InjectorViewFactory;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.debug.DebugView;
import io.bitsquare.gui.popups.EmptyWalletPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.SendAlertMessagePopup;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.storage.Storage;
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
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.bitcoinj.crypto.DRMWorkaround;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.controlsfx.dialog.Dialogs;
import org.reactfx.EventStreams;
import org.reactfx.util.FxTimer;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.bitsquare.app.BitsquareEnvironment.APP_NAME_KEY;

public class BitsquareApp extends Application {
    private static final Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BitsquareApp.class);

    public static final boolean DEV_MODE = false;

    private static Environment env;

    private BitsquareAppModule bitsquareAppModule;
    private Injector injector;

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    private static Stage primaryStage;
    private Scene scene;
    private final List<String> corruptedDatabaseFiles = new ArrayList<>();
    private MainView mainView;

    public static Runnable shutDownHandler;
    public static Runnable restartDownHandler;

    public static void setEnvironment(Environment env) {
        BitsquareApp.env = env;
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        BitsquareApp.primaryStage = primaryStage;

        Log.setup(Paths.get(env.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY), "bitsquare").toString());
        Log.PRINT_TRACE_METHOD = DEV_MODE;

        UserThread.setExecutor(Platform::runLater);

        shutDownHandler = this::stop;
        restartDownHandler = this::restart;

        // setup UncaughtExceptionHandler
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            // Might come from another thread 
            log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
            log.error("Uncaught Exception throwableMessage= " + throwable.getMessage());
            throwable.printStackTrace();
            UserThread.execute(() -> showErrorPopup(throwable, false));
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);

        DRMWorkaround.maybeDisableExportControls();

        Security.addProvider(new BouncyCastleProvider());

        try {
            // Guice
            bitsquareAppModule = new BitsquareAppModule(env, primaryStage);
            injector = Guice.createInjector(bitsquareAppModule);
            injector.getInstance(InjectorViewFactory.class).setInjector(injector);

            Version.NETWORK_ID = injector.getInstance(BitsquareEnvironment.class).getBitcoinNetwork().ordinal();

            // load the main view and create the main scene
            CachingViewLoader viewLoader = injector.getInstance(CachingViewLoader.class);
            mainView = (MainView) viewLoader.load(MainView.class);
            mainView.setPersistedFilesCorrupted(corruptedDatabaseFiles);

            Storage.setDatabaseCorruptionHandler((String fileName) -> {
                corruptedDatabaseFiles.add(fileName);
                if (mainView != null)
                    mainView.setPersistedFilesCorrupted(corruptedDatabaseFiles);
            });

            scene = new Scene(mainView.getRoot(), 1000, 740);
            scene.getStylesheets().setAll(
                    "/io/bitsquare/gui/bitsquare.css",
                    "/io/bitsquare/gui/images.css");

            // configure the system tray
            SystemTray.create(primaryStage, this::stop);
            primaryStage.setOnCloseRequest(e -> {
                e.consume();
                stop();
            });
            scene.setOnKeyReleased(keyEvent -> {
                // For now we exit when closing/quit the app.
                // Later we will only hide the window (systemTray.hideStage()) and use the exit item in the system tray for
                // shut down.
                if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN).match(keyEvent) ||
                        new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                    stop();
                else if (new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                    //if (BitsquareApp.DEV_MODE)
                    showDebugWindow();
                else if (new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                    showFPSWindow();
                else if (new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                    showEmptyWalletPopup();
                else if (new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                    showSendAlertMessagePopup();
            });


            // configure the primary stage
            primaryStage.setTitle(env.getRequiredProperty(APP_NAME_KEY));
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(750);
            primaryStage.setMinHeight(620);

            // on windows the title icon is also used as task bar icon in a larger size
            // on Linux no title icon is supported but also a large task bar icon is derived form that title icon
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

            //showDebugWindow();
        } catch (Throwable throwable) {
            showErrorPopup(throwable, false);
        }
    }

    private void showSendAlertMessagePopup() {
        AlertManager alertManager = injector.getInstance(AlertManager.class);
        new SendAlertMessagePopup()
                .onAddAlertMessage((alertMessage, privKeyString) -> alertManager.addAlertMessageIfKeyIsValid(alertMessage, privKeyString))
                .onRemoveAlertMessage(privKeyString -> alertManager.removeAlertMessageIfKeyIsValid(privKeyString))
                .show();
    }

    private void showEmptyWalletPopup() {
        injector.getInstance(EmptyWalletPopup.class).show();
    }

    private void showErrorPopup(Throwable throwable, boolean doShutDown) {
        if (scene == null) {
            scene = new Scene(new StackPane(), 1000, 650);
            primaryStage.setScene(scene);
            primaryStage.show();
        }
        try {
            throwable.printStackTrace();
            try {
                String message = throwable.getMessage();
                if (message != null)
                    new Popup().error(message).show();
                else
                    new Popup().error(throwable.toString()).show();
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

    //TODO just temp.
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
        gracefulShutDown(() -> {
            log.info("App shutdown complete");
            System.exit(0);
        });
    }

    private void gracefulShutDown(ResultHandler resultHandler) {
        log.debug("gracefulShutDown");
        try {
            if (injector != null) {
                OpenOfferManager openOfferManager = injector.getInstance(OpenOfferManager.class);
                openOfferManager.shutDown(() -> {
                    P2PService p2PService = injector.getInstance(P2PService.class);
                    p2PService.shutDown(() -> {
                        WalletService walletService = injector.getInstance(WalletService.class);
                        walletService.shutDownDone.addListener((observable, oldValue, newValue) -> {
                            bitsquareAppModule.close(injector);
                            resultHandler.handleResult();
                        });
                        injector.getInstance(WalletService.class).shutDown();
                    });
                });
                // we wait max 5 sec.
                FxTimer.runLater(Duration.ofMillis(5000), resultHandler::handleResult);
            } else {
                FxTimer.runLater(Duration.ofMillis(500), resultHandler::handleResult);
            }
        } catch (Throwable t) {
            log.info("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void restart() {
        //TODO
        stop();
        //gracefulShutDown(UpdateFX::restartApp);
    }
}
