package io.bitsquare;

import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.utils.BriefLogFormatter;
import com.google.bitcoin.utils.Threading;
import com.google.common.base.Throwables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.btc.IWalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.setup.ISetup;
import io.bitsquare.setup.MockSetup;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BitSquare extends Application
{
    private static final Logger log = LoggerFactory.getLogger(BitSquare.class);
    private IWalletFacade walletFacade;

    public static void main(String[] args)
    {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception
    {
        // Show the crash dialog for any exceptions that we don't handle and that hit the main loop.
        //GuiUtils.handleCrashesOnThisThread();
        try
        {
            init(stage);
        } catch (Throwable t)
        {
            // Nicer message for the case where the block store file is locked.
            if (Throwables.getRootCause(t) instanceof BlockStoreException)
            {
                //GuiUtils.informationalAlert("Already running", "This application is already running and cannot be started twice.");
            }
            else
            {
                throw t;
            }
        }
    }

    @Override
    public void stop() throws Exception
    {
        walletFacade.terminateWallet();

        super.stop();
    }

    private void init(Stage stage) throws IOException
    {
        // Make log output concise.
        BriefLogFormatter.init();

        // Tell bitcoinj to run event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;

        final Injector injector = Guice.createInjector(new BitSquareModule());
        walletFacade = injector.getInstance(IWalletFacade.class);

        final ISetup setup = injector.getInstance(MockSetup.class);
        setup.applyPersistedData();

        stage.setTitle("BitSquare");

        // main view
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(injector);
        final Parent mainView = loader.load(BitSquare.class.getResourceAsStream("/io/bitsquare/gui/MainView.fxml"));
        final Scene scene = new Scene(mainView, 800, 600);
        stage.setScene(scene);

        // apply css
        final String global = getClass().getResource("/io/bitsquare/gui/global.css").toExternalForm();
        // final String textValidation = getClass().getResource("/wallettemplate/utils/text-validation.css").toExternalForm();
        //scene.getStylesheets().setAll(global, textValidation);
        scene.getStylesheets().setAll(global);

        stage.show();
    }
}
