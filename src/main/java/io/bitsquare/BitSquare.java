package io.bitsquare;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.settings.Startup;
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
    private WalletFacade walletFacade;

    public static void main(String[] args)
    {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception
    {
        final Injector injector = Guice.createInjector(new BitSquareModule());
        walletFacade = injector.getInstance(WalletFacade.class);

        // apply stored data
        final User user = injector.getInstance(User.class);
        final Storage storage = injector.getInstance(Storage.class);
        user.updateFromStorage((User) storage.read(user.getClass().getName()));

        //TODO remove
        final Startup setup = injector.getInstance(Startup.class);
        setup.applyPersistedData();

        stage.setTitle("BitSquare");

        GuiceFXMLLoader.setInjector(injector);
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource("/io/bitsquare/gui/MainView.fxml"), Localisation.getResourceBundle());
        final Parent mainView = loader.load();
        final Scene scene = new Scene(mainView, 800, 600);
        stage.setScene(scene);

        final String global = getClass().getResource("/io/bitsquare/gui/global.css").toExternalForm();
        scene.getStylesheets().setAll(global);

        stage.show();
    }

    @Override
    public void stop() throws Exception
    {
        walletFacade.terminateWallet();

        super.stop();
    }
}
