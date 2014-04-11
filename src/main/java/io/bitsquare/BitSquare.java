package io.bitsquare;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.setup.ISetup;
import io.bitsquare.setup.MockSetup;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BitSquare extends Application
{
    private static final Logger log = LoggerFactory.getLogger(BitSquare.class);

    public static void main(String[] args)
    {
        launch(args);
    }

    @Override
    public void start(Stage stage)
    {
        Injector injector = Guice.createInjector(new BitSquareModule());
        ISetup setup = injector.getInstance(MockSetup.class);

        setup.applyPersistedData();

        stage.setTitle("BitSquare");

        GuiceFXMLLoader loader = new GuiceFXMLLoader(injector);
        try
        {
            Parent mainView = loader.load(BitSquare.class.getResourceAsStream("/io/bitsquare/gui/MainView.fxml"));
            Scene scene = new Scene(mainView, 800, 600);
            scene.getStylesheets().setAll(getClass().getResource("/io/bitsquare/gui/global.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

}
