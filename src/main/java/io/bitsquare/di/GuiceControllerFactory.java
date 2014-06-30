package io.bitsquare.di;

import com.google.inject.Injector;
import javafx.util.Callback;

/**
 * A JavaFX controller factory for constructing controllers via Guice DI. To
 * install this in the {@link javafx.fxml.FXMLLoader}, pass it as a parameter to
 * {@link javafx.fxml.FXMLLoader#setControllerFactory(Callback)}.
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
