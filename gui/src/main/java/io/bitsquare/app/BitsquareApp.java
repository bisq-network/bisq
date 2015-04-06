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

import io.bitsquare.gui.SystemTray;
import io.bitsquare.gui.common.view.CachingViewLoader;
import io.bitsquare.gui.common.view.View;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.common.view.guice.InjectorViewFactory;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.debug.DebugView;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.storage.FileManager;
import io.bitsquare.util.Utilities;

import org.bitcoinj.utils.Threading;

import com.google.common.base.Throwables;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;
import java.io.InvalidObjectException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

import static io.bitsquare.app.BitsquareEnvironment.APP_NAME_KEY;

public class BitsquareApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(BitsquareApp.class);

    private static Environment env;

    private BitsquareAppModule bitsquareAppModule;
    private Injector injector;
    private Stage primaryStage;
    private Scene scene;

    public static void setEnvironment(Environment env) {
        BitsquareApp.env = env;
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;

        log.trace("BitsquareApp.start");
        try {
            Threading.USER_THREAD = Platform::runLater;

            bitsquareAppModule = new BitsquareAppModule(env, primaryStage);
            injector = Guice.createInjector(bitsquareAppModule);
            injector.getInstance(InjectorViewFactory.class).setInjector(injector);

            FileManager.setUncaughtExceptionHandler(
                    (t, throwable) -> Platform.runLater(
                            () -> Popups.handleUncaughtExceptions(Throwables.getRootCause(throwable))
                    )
            );

            Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) ->
                    Popups.handleUncaughtExceptions(Throwables.getRootCause(throwable)));

            // load the main view and create the main scene
            log.trace("viewLoader.load(MainView.class)");
            CachingViewLoader viewLoader = injector.getInstance(CachingViewLoader.class);
            View view = viewLoader.load(MainView.class);

            scene = new Scene((Parent) view.getRoot(), 1000, 620);
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
                    showDebugWindow();
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

            log.trace("primaryStage.show");
            primaryStage.show();

            //TODO just temp.
            //showDebugWindow();
        } catch (Throwable t) {
            if (t instanceof InvalidObjectException) {
                Popups.openErrorPopup("There is a problem with different version of persisted objects. " +
                        "Please delete the db directory inside the app directory.");
            }
            log.error(t.toString());
        }
    }

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

    @Override
    public void stop() {
        bitsquareAppModule.close(injector);
        System.exit(0);
    }
}
