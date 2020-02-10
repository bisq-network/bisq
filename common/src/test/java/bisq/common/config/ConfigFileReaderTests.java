package bisq.common.config;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ConfigFileReaderTests {

    private File file;
    private PrintWriter writer;
    private ConfigFileReader reader;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws IOException {
        file = File.createTempFile("bisq", "properties");
        reader = new ConfigFileReader(file);
        writer = new PrintWriter(file);
    }

    @Test
    public void whenFileDoesNotExist_thenGetLinesThrows() {
        writer.close();
        assertTrue(file.delete());

        exception.expect(ConfigException.class);
        exception.expectMessage(containsString("Config file"));
        exception.expectMessage(containsString("does not exist"));

        reader.getLines();
    }

    @Test
    public void whenOptionHasWhitespaceAroundEqualsSign_thenGetLinesPreservesIt() {
        writer.println("name1 =arg1");
        writer.println("name2= arg2");
        writer.println("name3 =  arg3");
        writer.flush();

        assertThat(reader.getLines(), contains(
                "name1 =arg1",
                "name2= arg2",
                "name3 =  arg3"));
    }

    @Test
    public void whenOptionHasEscapedColons_thenTheyGetUnescaped() {
        writer.println("host1=example.com\\:8080");
        writer.println("host2=example.org:8080");
        writer.flush();

        assertThat(reader.getLines(), contains(
                "host1=example.com:8080",
                "host2=example.org:8080"));
    }

    @Test
    public void whenFileContainsNonOptionLines_getOptionLinesReturnsOnlyOptionLines() {
        writer.println("# Comment");
        writer.println("");
        writer.println("name1=arg1");
        writer.println("noArgOpt");
        writer.flush();

        assertThat(reader.getOptionLines(), contains("name1=arg1", "noArgOpt"));
    }
}
