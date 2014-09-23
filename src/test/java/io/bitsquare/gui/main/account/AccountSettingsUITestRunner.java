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

package io.bitsquare.gui.main.account;

import io.bitsquare.di.BitSquareModule;
import io.bitsquare.util.ViewLoader;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;

import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For testing single isolated UI screens
 */
public class AccountSettingsUITestRunner extends Application {
    private static final Logger log = LoggerFactory.getLogger(AccountSettingsUITestRunner.class);
    private Scene scene;
    private Pane view;
    private Pane pane;
    private boolean devTest = true;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        Injector injector = Guice.createInjector(new BitSquareModule());
        ViewLoader.setInjector(injector);

        pane = new StackPane();
        scene = new Scene(pane, 1000, 630);
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+S"), this::loadMainWindow);
        loadMainWindow();
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public void loadMainWindow() {
        log.debug("re load");
        pane.getChildren().removeAll();
        ViewLoader loader = new ViewLoader(
                getUrl("/io/bitsquare/gui/account/AccountSettingsView.fxml"), false);
        try {
            view = loader.load();
            pane.getChildren().setAll(view);
            refreshStylesheets();
        } catch (IOException e) {
            e.printStackTrace();
            e.printStackTrace();
        }
    }

    private void refreshStylesheets() {
        scene.getStylesheets().clear();
        scene.getStylesheets().setAll(getUrl("/io/bitsquare/gui/bitsquare.css").toExternalForm());
    }

    private URL getUrl(String subPath) {
        if (devTest) {
            try {
                // load from file system location to make a reload possible. makes dev process easier with hot reload
                return new URL("file:///Users/mk/Documents/_intellij/bitsquare/src/main/java" + subPath);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            }
        }
        else {
            return getClass().getResource(subPath);
        }
    }
}
