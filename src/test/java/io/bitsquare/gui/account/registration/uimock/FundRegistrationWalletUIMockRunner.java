package io.bitsquare.gui.account.registration.uimock;

import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;

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
public class FundRegistrationWalletUIMockRunner extends Application {
    private static final Logger log = LoggerFactory.getLogger(FundRegistrationWalletUIMockRunner.class);
    private Scene scene;
    private Parent view;
    private Pane pane;
    private boolean devTest = true;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        Injector injector = Guice.createInjector(new BitSquareModule());
        GuiceFXMLLoader.setInjector(injector);

        pane = new StackPane();
        scene = new Scene(pane, 1000, 1200);
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+S"), this::loadMainWindow);
        loadMainWindow();
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public void loadMainWindow() {
        log.debug("re load");
        pane.getChildren().removeAll();
        GuiceFXMLLoader loader = new GuiceFXMLLoader(
                getUrl("/io/bitsquare/gui/registration/uimock/FundRegistrationWalletViewUIMock.fxml"), false);

        try {
            view = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }

        pane.getChildren().setAll(view);
        refreshStylesheets();
    }

    private void refreshStylesheets() {
        scene.getStylesheets().clear();
        scene.getStylesheets().setAll(getUrl("/io/bitsquare/gui/bitsquare.css").toExternalForm());
    }

    private URL getUrl(String subPath) {
        if (devTest) {
            try {
                // load from file system location to make a reload possible. makes dev process easier with hot reload
                return new URL("file:///Users/mk/Documents/_intellij/bitsquare/src/test/java" + subPath);
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
