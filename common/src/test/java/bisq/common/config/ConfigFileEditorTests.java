package bisq.common.config;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ConfigFileEditorTests {

    private File file;
    private PrintWriter writer;
    private ConfigFileReader reader;
    private ConfigFileEditor editor;

    @Before
    public void setUp() throws IOException {
        file = File.createTempFile("bisq", "properties");
        reader = new ConfigFileReader(file);
        editor = new ConfigFileEditor(file);
        writer = new PrintWriter(file);
    }

    @Test
    public void whenFileDoesNotExist_thenSetOptionCreatesItAndAppendsOneLine() {
        writer.close();
        assertTrue(file.delete());

        editor.setOption("opt1", "val1");

        assertThat(reader.getLines(), contains("opt1=val1"));
    }

    @Test
    public void whenFileContainsOptionBeingSet_thenSetOptionOverwritesIt() {
        writer.println("opt1=val1");
        writer.println("opt2=val2");
        writer.println("opt3=val3");
        writer.flush();

        editor.setOption("opt2", "newval2");

        assertThat(reader.getLines(), contains(
                "opt1=val1",
                "opt2=newval2",
                "opt3=val3"));
    }

    @Test
    public void whenOptionBeingSetHasNoArg_thenSetOptionWritesItWithNoEqualsSign() {
        writer.println("opt1=val1");
        writer.println("opt2=val2");
        writer.flush();

        editor.setOption("opt3");

        assertThat(reader.getLines(), contains(
                "opt1=val1",
                "opt2=val2",
                "opt3"));
    }

    @Test
    public void whenFileHasBlankOrCommentLines_thenTheyArePreserved() {
        writer.println("# Comment 1");
        writer.println("opt1=val1");
        writer.println();
        writer.println("# Comment 2");
        writer.println("opt2=val2");
        writer.flush();

        editor.setOption("opt3=val3");

        assertThat(reader.getLines(), contains(
                "# Comment 1",
                "opt1=val1",
                "",
                "# Comment 2",
                "opt2=val2",
                "opt3=val3"));
    }

    @Test
    public void whenFileContainsOptionBeingCleared_thenClearOptionRemovesIt() {
        writer.println("opt1=val1");
        writer.println("opt2=val2");
        writer.flush();

        editor.clearOption("opt2");

        assertThat(reader.getLines(), contains("opt1=val1"));
    }

    @Test
    public void whenFileDoesNotContainOptionBeingCleared_thenClearOptionIsNoOp() {
        writer.println("opt1=val1");
        writer.println("opt2=val2");
        writer.flush();

        editor.clearOption("opt3");

        assertThat(reader.getLines(), contains(
                "opt1=val1",
                "opt2=val2"));
    }

    @Test
    public void whenFileDoesNotExist_thenClearOptionIsNoOp() {
        writer.close();
        assertTrue(file.delete());
        editor.clearOption("opt1");
        assertFalse(file.exists());
    }
}
