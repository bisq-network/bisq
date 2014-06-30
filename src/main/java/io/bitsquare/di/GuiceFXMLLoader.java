package io.bitsquare.di;

import com.google.inject.Injector;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Guice support for fxml controllers
 */
public class GuiceFXMLLoader extends FXMLLoader
{

    @Nullable
    private static Injector injector = null;

    // not used yet
   /* public GuiceFXMLLoader()
    {
        super();
        setupControllerFactory();
    }

    public GuiceFXMLLoader(URL url)
    {
        super(url);
        setupControllerFactory();
    }

    public GuiceFXMLLoader(URL url, ResourceBundle resourceBundle, BuilderFactory builderFactory)
    {
        super(url, resourceBundle, builderFactory);
        setupControllerFactory();
    }  */

    public GuiceFXMLLoader(URL url, ResourceBundle resourceBundle)
    {
        super(url, resourceBundle);
        setupControllerFactory();
    }


    public static void setInjector(@Nullable Injector injector)
    {
        GuiceFXMLLoader.injector = injector;
    }

    private void setupControllerFactory()
    {
        if (GuiceFXMLLoader.injector != null)
            setControllerFactory(new GuiceControllerFactory(GuiceFXMLLoader.injector));
    }

}
