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

package io.bitsquare.app.gui;

import io.bitsquare.BitsquareException;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.SystemTray;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import org.springframework.core.env.Environment;

import static io.bitsquare.app.BitsquareEnvironment.*;

public class BitsquareApp extends Application {
    private static Environment env;

    private BitsquareAppModule bitsquareAppModule;
    private Injector injector;

    public static void setEnvironment(Environment env) {
        BitsquareApp.env = env;
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        Preconditions.checkArgument(env != null, "Environment must not be null");

        bitsquareAppModule = new BitsquareAppModule(env, primaryStage);
        injector = Guice.createInjector(bitsquareAppModule);


        // route uncaught exceptions to a user-facing dialog

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) ->
                Popups.handleUncaughtExceptions(Throwables.getRootCause(throwable)));


        // initialize the application data directory (if necessary)

        initAppDir(env.getRequiredProperty(APP_DATA_DIR_KEY));


        // load and apply any stored settings

        User user = injector.getInstance(User.class);
        Settings settings = injector.getInstance(Settings.class);
        Persistence persistence = injector.getInstance(Persistence.class);
        persistence.init();

        User persistedUser = (User) persistence.read(user);
        user.applyPersistedUser(persistedUser);

        settings.applyPersistedSettings((Settings) persistence.read(settings.getClass().getName()));


        // load the main view and create the main scene

        ViewLoader.setInjector(injector);
        ViewLoader loader = new ViewLoader(Navigation.Item.MAIN, false);
        Parent view = loader.load();

        Scene scene = new Scene(view, 1000, 600);
        scene.getStylesheets().setAll(
                "/io/bitsquare/gui/bitsquare.css",
                "/io/bitsquare/gui/images.css");


        // configure the system tray

        SystemTray systemTray = new SystemTray(primaryStage, this::stop);
        primaryStage.setOnCloseRequest(e -> systemTray.hideStage());
        scene.setOnKeyReleased(keyEvent -> {
            // For now we exit when closing/quit the app.
            // Later we will only hide the window (systemTray.hideStage()) and use the exit item in the system tray for
            // shut down.
            if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN).match(keyEvent) ||
                    new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                stop();
        });


        // configure the primary stage

        primaryStage.setTitle(env.getRequiredProperty(APP_NAME_KEY));
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(75);
        primaryStage.setMinHeight(50);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream(
                ImageUtil.isRetina() ? "/images/window_icon@2x.png" : "/images/window_icon.png")));


        // make the UI visible

        primaryStage.show();
    }

    @Override
    public void stop() {
        bitsquareAppModule.close(injector);
        System.exit(0);
    }


    private void initAppDir(String appDir) {
        Path dir = Paths.get(appDir);
        if (Files.exists(dir)) {
            if (!Files.isWritable(dir)) {
                throw new BitsquareException("Application data directory '%s' is not writeable", dir);
            }
            return;
        }
        try {
            Files.createDirectory(dir);
        } catch (IOException ex) {
            throw new BitsquareException(ex, "Application data directory '%s' could not be created", dir);
        }
    }
}
