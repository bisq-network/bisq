package io.bitsquare.di;

import com.google.inject.Injector;
import javafx.fxml.FXMLLoader;

/**
 * Guice support for fxml controllers
 */
public class GuiceFXMLLoader extends FXMLLoader
{

    private static Injector injector = null;

    public GuiceFXMLLoader()
    {
        if (GuiceFXMLLoader.injector != null)
            setControllerFactory(new GuiceControllerFactory(GuiceFXMLLoader.injector));
    }

    public GuiceFXMLLoader(Injector injector)
    {
        if (GuiceFXMLLoader.injector == null)
        {
            GuiceFXMLLoader.injector = injector;
            setControllerFactory(new GuiceControllerFactory(GuiceFXMLLoader.injector));
        }
    }
}
