package lighthouse.threading;

import java.util.concurrent.Executor;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.*;

/**
 * An attempt to make multi-threading and observable/reactive UI programming work together inside JavaFX without too
 * many headaches. This class allows you to register change listeners on the target Observable which will be
 * run with the given {@link java.util.concurrent.Executor}. In this way an observable collection which is updated by
 * one thread can be observed from another thread without needing to use explicit locks or explicit marshalling.
 */
public class MarshallingObservers
{
    public static InvalidationListener addListener(Observable observable, InvalidationListener listener, Executor executor)
    {
        InvalidationListener l = x -> executor.execute(() -> listener.invalidated(x));
        observable.addListener(l);
        return l;
    }

    public static <T> ListChangeListener<T> addListener(ObservableList<T> observable, ListChangeListener<T> listener, Executor executor)
    {
        ListChangeListener<T> l = (ListChangeListener.Change<? extends T> c) -> executor.execute(() -> listener.onChanged(c));
        observable.addListener(l);
        return l;
    }

    public static <T> SetChangeListener<T> addListener(ObservableSet<T> observable, SetChangeListener<T> listener, Executor executor)
    {
        SetChangeListener<T> l = (SetChangeListener.Change<? extends T> c) -> executor.execute(() -> listener.onChanged(c));
        observable.addListener(l);
        return l;
    }

    public static <K, V> MapChangeListener<K, V> addListener(ObservableMap<K, V> observable, MapChangeListener<K, V> listener, Executor executor)
    {
        MapChangeListener<K, V> l = (MapChangeListener.Change<? extends K, ? extends V> c) -> executor.execute(() -> listener.onChanged(c));
        observable.addListener(l);
        return l;
    }
}
