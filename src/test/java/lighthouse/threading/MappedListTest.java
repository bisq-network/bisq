package lighthouse.threading;

import java.util.LinkedList;
import java.util.Queue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MappedListTest
{
    private ObservableList<String> inputs;
    private ObservableList<String> outputs;
    private Queue<ListChangeListener.Change<? extends String>> changes;

    @Before
    public void setup()
    {
        inputs = FXCollections.observableArrayList();
        outputs = new MappedList<>(inputs, str -> "Hello " + str);
        changes = new LinkedList<>();
        outputs.addListener(changes::add);
    }

    @Test
    public void add() throws Exception
    {
        assertEquals(0, outputs.size());
        inputs.add("Mike");
        ListChangeListener.Change<? extends String> change = getChange();
        assertTrue(change.wasAdded());
        assertEquals("Hello Mike", change.getAddedSubList().get(0));
        assertEquals(1, outputs.size());
        assertEquals("Hello Mike", outputs.get(0));
        inputs.remove(0);
        assertEquals(0, outputs.size());
    }

    private ListChangeListener.Change<? extends String> getChange()
    {
        ListChangeListener.Change<? extends String> change = changes.poll();
        change.next();
        return change;
    }

    @Test
    public void remove()
    {
        inputs.add("Mike");
        inputs.add("Dave");
        inputs.add("Katniss");
        getChange();
        getChange();
        getChange();
        assertEquals("Hello Mike", outputs.get(0));
        assertEquals("Hello Dave", outputs.get(1));
        assertEquals("Hello Katniss", outputs.get(2));
        inputs.remove(0);
        ListChangeListener.Change<? extends String> change = getChange();
        assertTrue(change.wasRemoved());
        assertEquals(2, outputs.size());
        assertEquals(1, change.getRemovedSize());
        assertEquals("Hello Mike", change.getRemoved().get(0));
        assertEquals("Hello Dave", outputs.get(0));

        inputs.remove(1);
        assertEquals(1, outputs.size());
        assertEquals("Hello Dave", outputs.get(0));
    }

    @Test
    public void replace() throws Exception
    {
        inputs.add("Mike");
        inputs.add("Dave");
        getChange();
        getChange();
        inputs.set(0, "Bob");
        assertEquals("Hello Bob", outputs.get(0));
        ListChangeListener.Change<? extends String> change = getChange();
        assertTrue(change.wasReplaced());
        assertEquals("Hello Mike", change.getRemoved().get(0));
        assertEquals("Hello Bob", change.getAddedSubList().get(0));
    }

    // Could also test permutation here if I could figure out how to actually apply one!
}