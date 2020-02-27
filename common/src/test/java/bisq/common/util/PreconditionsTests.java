package bisq.common.util;

import java.nio.file.Files;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static bisq.common.util.Preconditions.checkDir;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertSame;

public class PreconditionsTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void whenDirIsValid_thenDirIsReturned() throws IOException {
        File dir = Files.createTempDirectory("TestDir").toFile();
        File ret = checkDir(dir);
        assertSame(dir, ret);
    }

    @Test
    public void whenDirDoesNotExist_thenThrow() {
        String filepath = "/does/not/exist";
        if (System.getProperty("os.name").startsWith("Windows")) {
            filepath = "C:\\does\\not\\exist";
        }
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(equalTo(String.format("Directory '%s' does not exist", filepath)));
        checkDir(new File(filepath));
    }
}
