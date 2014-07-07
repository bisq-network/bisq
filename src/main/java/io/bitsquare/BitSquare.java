package io.bitsquare;

import com.google.common.base.Throwables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.popups.Popups;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.User;
import io.bitsquare.util.AWTSystemTray;
import io.bitsquare.util.FileUtil;
import io.bitsquare.util.StorageDirectory;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BitSquare extends Application
{
    private static final Logger log = LoggerFactory.getLogger(BitSquare.class);

    private static String APP_NAME = "bitsquare";
    private static Stage primaryStage;
    private WalletFacade walletFacade;
    private MessageFacade messageFacade;

    public static void main(String[] args)
    {
        log.debug("Startup: main " + Arrays.asList(args).toString());
        if (args != null && args.length > 0) APP_NAME = args[0];

        launch(args);
    }

    public static Stage getPrimaryStage()
    {
        return primaryStage;
    }

    public static String getAppName()
    {
        return APP_NAME;
    }

    public static String getUID()
    {
        return FileUtil.getApplicationFileName();
    }

    @Override
    public void start(Stage primaryStage) throws IOException
    {
        log.trace("Startup: start");
        BitSquare.primaryStage = primaryStage;

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> Popups.handleUncaughtExceptions(Throwables.getRootCause(throwable)));

        // use a local data dir as default storage dir (can be overwritten in the settings)
        // TODO save root preferences always in app dir top get preferred storage location
        StorageDirectory.setStorageDirectory(new File(StorageDirectory.getApplicationDirectory().getAbsolutePath() + "/data"));


        // currently there is not SystemTray support for java fx (planned for version 3) so we use the old AWT
        AWTSystemTray.createSystemTray(primaryStage);

        final Injector injector = Guice.createInjector(new BitSquareModule());

        walletFacade = injector.getInstance(WalletFacade.class);
        messageFacade = injector.getInstance(MessageFacade.class);
        log.trace("Startup: messageFacade, walletFacade inited");

        // apply stored data
        final User user = injector.getInstance(User.class);
        final Settings settings = injector.getInstance(Settings.class);
        final Storage storage = injector.getInstance(Storage.class);
        storage.init();
        user.updateFromStorage((User) storage.read(user.getClass().getName()));
        settings.updateFromStorage((Settings) storage.read(settings.getClass().getName()));

        primaryStage.setTitle("BitSquare (" + getUID() + ")");

        GuiceFXMLLoader.setInjector(injector);

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(NavigationItem.MAIN.getFxmlUrl()), Localisation.getResourceBundle());
        final Parent mainView = loader.load();
        BorderPane rootPane = new BorderPane();
        rootPane.setTop(getMenuBar());
        rootPane.setCenter(mainView);

        final Scene scene = new Scene(rootPane, 800, 600);
        scene.getStylesheets().setAll(getClass().getResource("/io/bitsquare/gui/bitsquare.css").toExternalForm());

        setupCloseHandlers(primaryStage, scene);

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(400);
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);

        primaryStage.show();

        log.debug("Startup: stage displayed");
    }

    private void setupCloseHandlers(Stage primaryStage, Scene scene)
    {
        primaryStage.setOnCloseRequest(e -> AWTSystemTray.setStageHidden());

        KeyCodeCombination keyCodeCombination = new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN);
        scene.setOnKeyReleased(keyEvent -> {
            if (keyCodeCombination.match(keyEvent)) AWTSystemTray.setStageHidden();
        });
    }

    private MenuBar getMenuBar()
    {
        MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(true);

        Menu fileMenu = new Menu("_File");
        fileMenu.setMnemonicParsing(true);
        MenuItem backupMenuItem = new MenuItem("Backup wallet");
        fileMenu.getItems().addAll(backupMenuItem);

        Menu settingsMenu = new Menu("_Settings");
        settingsMenu.setMnemonicParsing(true);
        MenuItem changePwMenuItem = new MenuItem("Change password");
        settingsMenu.getItems().addAll(changePwMenuItem);

        Menu helpMenu = new Menu("_Help");
        helpMenu.setMnemonicParsing(true);
        MenuItem faqMenuItem = new MenuItem("FAQ");
        MenuItem forumMenuItem = new MenuItem("Forum");
        helpMenu.getItems().addAll(faqMenuItem, forumMenuItem);

        menuBar.getMenus().setAll(fileMenu, settingsMenu, helpMenu);
        return menuBar;
    }

    @Override
    public void stop() throws Exception
    {
        walletFacade.shutDown();
        messageFacade.shutDown();

        super.stop();
        System.exit(0);
    }
}
