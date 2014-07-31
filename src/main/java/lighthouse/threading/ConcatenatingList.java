package lighthouse.threading;

import java.util.ArrayList;
import java.util.List;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableListBase;
import javafx.collections.WeakListChangeListener;

/**
 * This list is created by dynamically concatenating all the source lists together.
 */
public class ConcatenatingList<T> extends ObservableListBase<T> implements ObservableList<T>
{
    private List<ObservableList<T>> sources = new ArrayList<>();
    private ListChangeListener<T> listener = this::sourceChanged;

    @SafeVarargs
    public ConcatenatingList(ObservableList<T>... source)
    {
        super();
        for (ObservableList<T> s : source)
        {
            sources.add(s);
            s.addListener(new WeakListChangeListener<T>(listener));
        }
        if (sources.isEmpty())
            throw new IllegalArgumentException();
    }

    private int calculateOffset(ObservableList<? extends T> source)
    {
        int cursor = 0;
        for (ObservableList<T> ts : sources)
        {
            if (ts == source) return cursor;
            cursor += ts.size();
        }
        return cursor;
    }

    private void sourceChanged(ListChangeListener.Change<? extends T> c)
    {
        ObservableList<? extends T> source = c.getList();
        int offset = calculateOffset(source);
        beginChange();
        while (c.next())
        {
            if (c.wasPermutated())
            {
                int[] perm = new int[c.getTo() - c.getFrom()];
                for (int i = c.getFrom(); i < c.getTo(); i++)
                    perm[i - c.getFrom()] = c.getPermutation(i) + offset;
                nextPermutation(c.getFrom() + offset, c.getTo() + offset, perm);
            }
            else if (c.wasUpdated())
            {
                for (int i = c.getFrom(); i < c.getTo(); i++)
                {
                    nextUpdate(i + offset);
                }
            }
            else
            {
                if (c.wasRemoved())
                {
                    // Removed should come first to properly handle replacements, then add.
                    nextRemove(c.getFrom() + offset, c.getRemoved());
                }
                if (c.wasAdded())
                {
                    nextAdd(c.getFrom() + offset, c.getTo() + offset);
                }
            }
        }
        endChange();
    }

    @Override
    public T get(int index)
    {
        for (ObservableList<T> source : sources)
        {
            if (index < source.size())
            {
                return source.get(index);
            }
            else
            {
                index -= source.size();
            }
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int size()
    {
        return sources.stream().mapToInt(List::size).sum();
    }
}