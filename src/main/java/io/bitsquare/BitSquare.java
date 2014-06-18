package io.bitsquare;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.User;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitSquare extends Application
{
    private static final Logger log = LoggerFactory.getLogger(BitSquare.class);
    public static String ID = "bitsquare";
    private static Stage stage;
    private WalletFacade walletFacade;
    private MessageFacade messageFacade;

    public static void main(String[] args)
    {
        log.debug("Startup: main");
        if (args.length > 0)
            ID = args[0];

        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception
    {
        BitSquare.stage = stage;
        log.debug("Startup: start");
        final Injector injector = Guice.createInjector(new BitSquareModule());
        walletFacade = injector.getInstance(WalletFacade.class);

        messageFacade = injector.getInstance(MessageFacade.class);
        log.debug("Startup: messageFacade, walletFacade inited");

        // apply stored data
        final User user = injector.getInstance(User.class);
        final Settings settings = injector.getInstance(Settings.class);
        final Storage storage = injector.getInstance(Storage.class);
        user.updateFromStorage((User) storage.read(user.getClass().getName()));

        settings.updateFromStorage((Settings) storage.read(settings.getClass().getName()));

        if (ID.length() > 0)
            stage.setTitle("BitSquare (" + ID + ")");
        else
            stage.setTitle("BitSquare");


        GuiceFXMLLoader.setInjector(injector);
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource("/io/bitsquare/gui/MainView.fxml"), Localisation.getResourceBundle());
        final Parent mainView = loader.load();

        final Scene scene = new Scene(mainView, 800, 600);
        stage.setScene(scene);

        final String global = getClass().getResource("/io/bitsquare/gui/global.css").toExternalForm();
        scene.getStylesheets().setAll(global);

        stage.setMinWidth(800);
        stage.setMinHeight(400);
        stage.setWidth(800);
        stage.setHeight(600);

        stage.show();
        log.debug("Startup: stage displayed");
    }

    @Override
    public void stop() throws Exception
    {
        walletFacade.shutDown();
        messageFacade.shutDown();

        super.stop();
    }

    public static Stage getStage()
    {
        return stage;
    }
}
