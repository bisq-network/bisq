package bisq.common.config;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class TestConfig extends Config {

    public TestConfig() throws HelpRequested {
        super(generateTestAppName());
    }

    public TestConfig(String... args) {
        super(generateTestAppName(), args);
    }

    private static String generateTestAppName() {
        try {
            File file = File.createTempFile("Bisq", "Test");
            file.delete();
            return file.toPath().getFileName().toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
