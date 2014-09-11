package lighthouse.files;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO update to open source file when its released, check licence issues

/**
 * Manages the directory where the app stores all its files.
 */
public class AppDirectory {
    public static Path getUserDataDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getenv("APPDATA"));
        }
        else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support");
        }
        else {
            // Linux and other similar systems, we hope (not Android).
            return Paths.get(System.getProperty("user.home"), ".local", "share");
        }
    }

    public static Path getUserDataDir(String appName) {
        return getUserDataDir().resolve(appName);
    }

    public static Path initAppDir(String appName) throws IOException {
        AppDirectory.appName = appName;

        Path dir = dir();
        if (!Files.exists(dir))
            Files.createDirectory(dir);
        else if (!Files.isWritable(dir))
            throw new IOException("App directory is not writeable");
        return dir;
    }

    private static String appName = "";

    private static Path dir;

    public static Path dir() {
        if (dir == null)
            return getUserDataDir(appName);
        else
            return dir;
    }

    public static void overrideAppDir(Path newDir) {
        dir = checkNotNull(newDir);
    }
}
