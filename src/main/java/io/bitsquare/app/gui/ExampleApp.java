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

import org.bitcoinj.utils.BriefLogFormatter;

import com.google.common.util.concurrent.Uninterruptibles;

import java.io.IOException;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vinumeris.updatefx.AppDirectory;
import com.vinumeris.updatefx.Crypto;
import com.vinumeris.updatefx.UpdateFX;
import com.vinumeris.updatefx.UpdateSummary;
import com.vinumeris.updatefx.Updater;
import org.bouncycastle.math.ec.ECPoint;

// TODO remove it after we have impl. UpdateFX. 
// Let it here for reference and for easier test setup for the moment.
public class ExampleApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(ExampleApp.class);
    public static int VERSION = 3;

    public static void main(String[] args) throws IOException {
        // We want to store updates in our app dir so must init that here.
        AppDirectory.initAppDir("UpdateFX Example App");
        setupLogging();
        // re-enter at realMain, but possibly running a newer version of the software i.e. after this point the
        // rest of this code may be ignored.
        UpdateFX.bootstrap(ExampleApp.class, AppDirectory.dir(), args);
    }

    public static void realMain(String[] args) {
        launch(args);
    }

    private static java.util.logging.Logger logger;

    private static void setupLogging() throws IOException {
        logger = java.util.logging.Logger.getLogger("");
        logger.getHandlers()[0].setFormatter(new BriefLogFormatter());
        FileHandler handler = new FileHandler(AppDirectory.dir().resolve("log.txt").toString(), true);
        handler.setFormatter(new BriefLogFormatter());
        logger.addHandler(handler);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
        Thread.currentThread().setContextClassLoader(ExampleApp.class.getClassLoader());
        // Must be done twice for the times when we come here via realMain.
        AppDirectory.initAppDir("UpdateFX Example App");

        log.info("Hello World! This is version " + VERSION);

        ProgressIndicator indicator = showGiantProgressWheel(primaryStage);

        List<ECPoint> pubkeys = Crypto.decode("028B41BDDCDCAD97B6AE088FEECA16DC369353B717E13319370C729CB97D677A11",
                // wallet_1
                "031E3D80F21A4D10D385A32ABEDC300DACBEDBC839FBA58376FBD5D791D806BA68"); // wallet

        Updater updater = new Updater("http://localhost:8000/", "ExampleApp/" + VERSION, VERSION,
                AppDirectory.dir(), UpdateFX.findCodePath(ExampleApp.class),
                pubkeys, 1) {
            @Override
            protected void updateProgress(long workDone, long max) {
                super.updateProgress(workDone, max);
                // Give UI a chance to show.
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }
        };

        indicator.progressProperty().bind(updater.progressProperty());

        log.info("Checking for updates!");
        updater.setOnSucceeded(event -> {
            try {
                UpdateSummary summary = updater.get();
                if (summary.descriptions.size() > 0) {
                    log.info("One liner: {}", summary.descriptions.get(0).getOneLiner());
                    log.info("{}", summary.descriptions.get(0).getDescription());
                }
                if (summary.highestVersion > VERSION) {
                    log.info("Restarting to get version " + summary.highestVersion);
                    if (UpdateFX.getVersionPin(AppDirectory.dir()) == 0)
                        UpdateFX.restartApp();
                }
            } catch (Throwable e) {
                log.error("oops", e);
            }
        });
        updater.setOnFailed(event -> {
            log.error("Update error: {}", updater.getException());
            updater.getException().printStackTrace();
        });

        indicator.setOnMouseClicked(ev -> UpdateFX.restartApp());

        new Thread(updater, "UpdateFX Thread").start();

        primaryStage.show();
    }

    private ProgressIndicator showGiantProgressWheel(Stage stage) {
        ProgressIndicator indicator = new ProgressIndicator();
        BorderPane borderPane = new BorderPane(indicator);
        borderPane.setMinWidth(640);
        borderPane.setMinHeight(480);
        Button pinButton = new Button();
        pinButton.setText("Pin to version 1");
        pinButton.setOnAction(event -> {
            UpdateFX.pinToVersion(AppDirectory.dir(), 1);
            UpdateFX.restartApp();
        });
        HBox box = new HBox(new Label("Version " + VERSION), pinButton);
        box.setSpacing(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10));
        borderPane.setTop(box);
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        return indicator;
    }
}
