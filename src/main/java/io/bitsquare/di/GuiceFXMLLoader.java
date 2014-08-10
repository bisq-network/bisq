package io.bitsquare.di;

import com.google.inject.Injector;
import io.bitsquare.locale.Localisation;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice support for fxml controllers
 * Support caching. Speed up switches between UI screens.
 */
public class GuiceFXMLLoader
{
    private static final Logger log = LoggerFactory.getLogger(GuiceFXMLLoader.class);
    private static Injector injector = null;
    private FXMLLoader loader;
    private final boolean isCached;
    private final URL url;
    private Item item;

    public static void setInjector(Injector injector)
    {
        GuiceFXMLLoader.injector = injector;
    }

    // TODO maybe add more sophisticated caching strategy with removal of rarely accessed items
    private static final Map<URL, Item> cachedGUIItems = new HashMap<>();

    public GuiceFXMLLoader(URL url)
    {
        this(url, true);
    }

    public GuiceFXMLLoader(URL url, boolean useCaching)
    {
        this.url = url;

        isCached = useCaching && cachedGUIItems.containsKey(url);

        if (!isCached)
        {
            loader = new FXMLLoader(url, Localisation.getResourceBundle());
            if (GuiceFXMLLoader.injector != null)
                loader.setControllerFactory(new GuiceControllerFactory(GuiceFXMLLoader.injector));
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T load() throws java.io.IOException
    {
        if (isCached)
        {
            item = cachedGUIItems.get(url);
            log.debug("loaded from cache " + url);
            return (T) cachedGUIItems.get(url).view;
        }
        else
        {
            log.debug("load from disc " + url);
            T result = loader.load();
            item = new Item(result, loader.getController());
            cachedGUIItems.put(url, item);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getController()
    {
        return (T) item.controller;
    }


    class Item<T>
    {
        final T view;
        final T controller;

        Item(T view, T controller)
        {
            this.view = view;
            this.controller = controller;
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

