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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.gui.AWTSystemTray;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.msg.DHTSeedService;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.actor.event.PeerInitialized;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;
import io.bitsquare.util.BitsquareArgumentParser;
import io.bitsquare.util.ViewLoader;

import com.google.common.base.Throwables;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;

import java.util.Arrays;

import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import lighthouse.files.AppDirectory;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class BitSquare extends Application {
    private static final Logger log = LoggerFactory.getLogger(BitSquare.class);

    public static final boolean fillFormsWithDummyData = true;

    private static String APP_NAME = "Bitsquare";
    private static Injector injector;
    private static Stage primaryStage;
    private WalletFacade walletFacade;
    private MessageFacade messageFacade;

    public static void main(String[] args) {
        Profiler.init();
        Profiler.printMsgWithTime("BitSquare.main called with args " + Arrays.asList(args).toString());

        injector = Guice.createInjector(new BitSquareModule());

        BitsquareArgumentParser parser = new BitsquareArgumentParser();
        Namespace namespace = null;
        try {
            //System.out.println(parser.parseArgs(args));
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        if (namespace != null) {

            if (namespace.getString(BitsquareArgumentParser.NAME_FLAG) != null) {
                APP_NAME = APP_NAME + "-" + namespace.getString(BitsquareArgumentParser.NAME_FLAG);
            }

            Integer port = BitsquareArgumentParser.PORT_DEFAULT;
            if (namespace.getString(BitsquareArgumentParser.PORT_FLAG) != null) {
                port = Integer.valueOf(namespace.getString(BitsquareArgumentParser.PORT_FLAG));
            }
            if (namespace.getBoolean(BitsquareArgumentParser.SEED_FLAG) == true) {
                DHTSeedService dhtSeed = injector.getInstance(DHTSeedService.class);
                dhtSeed.setHandler(m -> {
                    if (m instanceof PeerInitialized) {
                        System.out.println("Seed Peer Initialized on port " + ((PeerInitialized) m).getPort
                                ());
                    }
                });
                dhtSeed.initializePeer("localhost", port);
            }
            else {
                launch(args);
            }
        }

//        Thread seedNodeThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                startSeedNode();
//            }
//        });
//        seedNodeThread.start();

    }

//    private static void startSeedNode() {
//        List<SeedNodeAddress.StaticSeedNodeAddresses> staticSedNodeAddresses = SeedNodeAddress
//                .StaticSeedNodeAddresses.getAllSeedNodeAddresses();
//        SeedNode seedNode = new SeedNode(new SeedNodeAddress(staticSedNodeAddresses.get(0)));
//        seedNode.setDaemon(true);
//        seedNode.start();
//
//        try {
//            // keep main thread up
//            Thread.sleep(Long.MAX_VALUE);
//            log.debug("Localhost seed node started");
//        } catch (InterruptedException e) {
//            log.error(e.toString());
//        }
//    }


    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static String getAppName() {
        return APP_NAME;
    }

    @Override
    public void start(Stage primaryStage) {
        Profiler.printMsgWithTime("BitSquare.start called");
        BitSquare.primaryStage = primaryStage;

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> Popups.handleUncaughtExceptions
                (Throwables.getRootCause(throwable)));

        try {
            AppDirectory.initAppDir(APP_NAME);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

//        final Injector injector = Guice.createInjector(new BitSquareModule());

        // currently there is not SystemTray support for java fx (planned for version 3) so we use the old AWT
        AWTSystemTray.createSystemTray(primaryStage, injector.getInstance(ActorSystem.class));

        walletFacade = injector.getInstance(WalletFacade.class);
        messageFacade = injector.getInstance(MessageFacade.class);
        Profiler.printMsgWithTime("BitSquare: messageFacade, walletFacade created");

        // apply stored data
        final User user = injector.getInstance(User.class);
        final Settings settings = injector.getInstance(Settings.class);
        final Persistence persistence = injector.getInstance(Persistence.class);
        persistence.init();

        User persistedUser = (User) persistence.read(user);
        user.applyPersistedUser(persistedUser);

        settings.applyPersistedSettings((Settings) persistence.read(settings.getClass().getName()));

        primaryStage.setTitle("BitSquare (" + APP_NAME + ")");

        // sometimes there is a rendering bug, see https://github.com/bitsquare/bitsquare/issues/160
        if (ImageUtil.isRetina())
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/window_icon@2x.png")));
        else
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/window_icon.png")));

        ViewLoader.setInjector(injector);

        final ViewLoader loader =
                new ViewLoader(getClass().getResource(Navigation.Item.MAIN.getFxmlUrl()), false);
        try {
            final Parent view = loader.load();

            final Scene scene = new Scene(view, 1000, 900);
            scene.getStylesheets().setAll(getClass().getResource("/io/bitsquare/gui/bitsquare.css").toExternalForm(),
                    getClass().getResource("/io/bitsquare/gui/images.css").toExternalForm());

            setupCloseHandlers(primaryStage, scene);

            primaryStage.setScene(scene);

            // TODO resizing not fully supported yet

            primaryStage.setMinWidth(75);
            primaryStage.setMinHeight(50);

          /*  primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(750);*/

            Profiler.initScene(primaryStage.getScene());

            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    private void setupCloseHandlers(Stage primaryStage, Scene scene) {
        primaryStage.setOnCloseRequest(e -> AWTSystemTray.setStageHidden());

        KeyCodeCombination keyCodeCombination = new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN);
        scene.setOnKeyReleased(keyEvent -> {
            if (keyCodeCombination.match(keyEvent)) AWTSystemTray.setStageHidden();
        });
    }

    @Override
    public void stop() throws Exception {
        walletFacade.shutDown();
        messageFacade.shutDown();

        super.stop();
        System.exit(0);
    }
}
