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

package io.bitsquare;

import io.bitsquare.di.BitsquareModule;
import io.bitsquare.gui.AWTSystemTray;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;
import io.bitsquare.util.ViewLoader;

import com.google.common.base.Throwables;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;

import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import lighthouse.files.AppDirectory;

public class BitsquareUI extends Application {
    private static final Logger log = LoggerFactory.getLogger(BitsquareUI.class);

    private static Stage primaryStage;

    private final BitsquareModule bitsquareModule;
    private final Injector injector;

    public BitsquareUI() {
        this.bitsquareModule = new BitsquareModule();
        this.injector = Guice.createInjector(bitsquareModule);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override
    public void start(Stage primaryStage) {
        BitsquareUI.primaryStage = primaryStage;

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> Popups.handleUncaughtExceptions
                (Throwables.getRootCause(throwable)));

        try {
            AppDirectory.initAppDir(Bitsquare.getAppName());
        } catch (IOException e) {
            log.error(e.getMessage());
        }


        // currently there is not SystemTray support for java fx (planned for version 3) so we use the old AWT
        AWTSystemTray.createSystemTray(primaryStage, injector.getInstance(ActorSystem.class), this);

        // apply stored data
        User user = injector.getInstance(User.class);
        Settings settings = injector.getInstance(Settings.class);
        Persistence persistence = injector.getInstance(Persistence.class);
        persistence.init();

        User persistedUser = (User) persistence.read(user);
        user.applyPersistedUser(persistedUser);

        settings.applyPersistedSettings((Settings) persistence.read(settings.getClass().getName()));

        primaryStage.setTitle("Bitsquare (" + Bitsquare.getAppName() + ")");

        // sometimes there is a rendering bug, see https://github.com/bitsquare/bitsquare/issues/160
        if (ImageUtil.isRetina())
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/window_icon@2x.png")));
        else
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/window_icon.png")));

        ViewLoader.setInjector(injector);

        ViewLoader loader = new ViewLoader(Navigation.Item.MAIN, false);
        Parent view = loader.load();

        Scene scene = new Scene(view, 1000, 600);
        scene.getStylesheets().setAll(
               "/io/bitsquare/gui/bitsquare.css",
               "/io/bitsquare/gui/images.css");

        setupCloseHandlers(primaryStage, scene);

        primaryStage.setScene(scene);

        primaryStage.setMinWidth(75);
        primaryStage.setMinHeight(50);

        primaryStage.show();
    }

    private void setupCloseHandlers(Stage primaryStage, Scene scene) {
        primaryStage.setOnCloseRequest(e -> AWTSystemTray.setStageHidden());

        KeyCodeCombination keyCodeCombination = new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN);
        scene.setOnKeyReleased(keyEvent -> {
            if (keyCodeCombination.match(keyEvent))
                AWTSystemTray.setStageHidden();
        });
    }

    @Override
    public void stop() {
        bitsquareModule.close(injector);
        System.exit(0);
    }
}
