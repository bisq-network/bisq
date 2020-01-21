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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(equalTo("Directory '/does/not/exist' does not exist"));
        checkDir(new File("/does/not/exist"));
    }
}
