package bisq.common.config;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigFileReaderTests {

    private File file;
    private PrintWriter writer;
    private ConfigFileReader reader;

    @BeforeEach
    public void setUp() throws IOException {
        file = File.createTempFile("bisq", "properties");
        reader = new ConfigFileReader(file);
        writer = new PrintWriter(file);
    }

    @Test
    public void whenFileDoesNotExist_thenGetLinesThrows() {
        writer.close();
        assertTrue(file.delete());

        Exception exception = assertThrows(ConfigException.class, () -> reader.getLines());

        String expectedMessage = "does not exist";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
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
