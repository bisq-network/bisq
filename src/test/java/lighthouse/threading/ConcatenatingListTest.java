package lighthouse.threading;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class ConcatenatingListTest
{
    @Test
    public void basic() throws Exception
    {
        ObservableList<String> a = FXCollections.observableArrayList();
        ObservableList<String> b = FXCollections.observableArrayList();
        ConcatenatingList<String> concat = new ConcatenatingList<>(a, b);
        assertEquals(0, concat.size());
        a.add("1");
        assertEquals(1, concat.size());
        assertEquals("1", concat.get(0));
        b.add("2");
        assertEquals(2, concat.size());
        assertEquals("2", concat.get(1));
    }
}