package bisq.price.util;

import org.springframework.core.io.FileSystemResource;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionControllerTest {

    @Test
    public void getVersion() throws IOException {
        VersionController controller = new VersionController(
                new FileSystemResource("src/main/resources/version.txt"));

        String version = controller.getVersion();
        assertTrue(version.length() > 0);
    }
}
