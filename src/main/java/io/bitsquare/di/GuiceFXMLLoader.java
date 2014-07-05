package io.bitsquare.di;

import com.google.inject.Injector;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;

/**
 * Guice support for fxml controllers
 */
public class GuiceFXMLLoader extends FXMLLoader
{
    private static Injector injector = null;

    public static void setInjector(Injector injector)
    {
        GuiceFXMLLoader.injector = injector;
    }

    public GuiceFXMLLoader(URL url, ResourceBundle resourceBundle)
    {
        super(url, resourceBundle);
        setupControllerFactory();
    }

    private void setupControllerFactory()
    {
        if (GuiceFXMLLoader.injector != null)
        {
            setControllerFactory(new GuiceControllerFactory(GuiceFXMLLoader.injector));
        }
    }

}
