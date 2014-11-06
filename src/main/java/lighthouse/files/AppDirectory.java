/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

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
        Path dir = dir(appName);
        if (!Files.exists(dir))
            Files.createDirectory(dir);
        else if (!Files.isWritable(dir))
            throw new IOException("App directory is not writeable");
        return dir;
    }

    private static Path dir;

    public static Path dir(String appName) {
        if (dir == null)
            return getUserDataDir(appName);
        else
            return dir;
    }

    public static void overrideAppDir(Path newDir) {
        dir = checkNotNull(newDir);
    }
}
