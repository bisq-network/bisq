package io.bitsquare.di;

import com.google.inject.Injector;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.util.Callback;

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
   // private static ClassLoader cachingClassLoader = new CachingClassLoader(FXMLLoader.getDefaultClassLoader());
    public GuiceFXMLLoader(URL url, ResourceBundle resourceBundle)
    {
        super(url, resourceBundle);
        // might be helpful for performance, but need further profiling. has memory drawbacks
        //setClassLoader(cachingClassLoader);
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

/**
 * A JavaFX controller factory for constructing controllers via Guice DI. To
 * install this in the {@link javafx.fxml.FXMLLoader}, pass it as a parameter to
 * {@link javafx.fxml.FXMLLoader#setControllerFactory(javafx.util.Callback)}.
 * <p>
 * Once set, make sure you do <b>not</b> use the static methods on
 * {@link javafx.fxml.FXMLLoader} when creating your JavaFX node.
 */
class GuiceControllerFactory implements Callback<Class<?>, Object>
{

    private final Injector injector;

    public GuiceControllerFactory(Injector injector)
    {
        this.injector = injector;
    }

    @Override
    public Object call(Class<?> aClass)
    {
        return injector.getInstance(aClass);
    }
}

