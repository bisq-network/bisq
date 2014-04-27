package io.bitsquare.di;

import com.google.inject.Injector;
import javafx.fxml.FXMLLoader;
import javafx.util.BuilderFactory;

import java.net.URL;
import java.util.ResourceBundle;

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

    public GuiceFXMLLoader()
    {
        super();
        setupControllerFactory();
    }

    public GuiceFXMLLoader(URL url)
    {
        super(url);
        setupControllerFactory();
    }

    public GuiceFXMLLoader(URL url, ResourceBundle resourceBundle)
    {
        super(url, resourceBundle);
        setupControllerFactory();
    }

    public GuiceFXMLLoader(URL url, ResourceBundle resourceBundle, BuilderFactory builderFactory)
    {
        super(url, resourceBundle, builderFactory);
        setupControllerFactory();
    }

    private void setupControllerFactory()
    {
        if (GuiceFXMLLoader.injector != null)
            setControllerFactory(new GuiceControllerFactory(GuiceFXMLLoader.injector));
    }

}
