/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TorSetupTest {

    @Test
    void cleanupObsoleteTorFilesDeletesOnlyTorReal(@TempDir Path torDir) throws IOException {
        createTorDir(torDir);
        Files.writeString(torDir.resolve("tor.real"), "old tor binary");
        Map<String, String> expected = readAllFiles(torDir);
        expected.remove("tor.real");

        new TorSetup(torDir.toFile()).cleanupObsoleteTorFiles();

        assertFalse(Files.exists(torDir.resolve("tor.real")));
        assertEquals(expected, readAllFiles(torDir));
        assertEquals("abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrstuvwx.onion\n",
                Files.readString(torDir.resolve("hiddenservice").resolve("hostname")));
    }

    @Test
    void cleanupObsoleteTorFilesKeepsTorDirWithoutObsoleteFiles(@TempDir Path torDir) throws IOException {
        createTorDir(torDir);
        Map<String, String> expected = readAllFiles(torDir);

        new TorSetup(torDir.toFile()).cleanupObsoleteTorFiles();

        assertEquals(expected, readAllFiles(torDir));
        assertTrue(Files.isDirectory(torDir.resolve("hiddenservice")));
    }

    private static void createTorDir(Path torDir) throws IOException {
        Files.writeString(torDir.resolve("tor"), "tor binary");
        Files.writeString(torDir.resolve("libevent-2.1.7.dylib"), "libevent");
        Files.writeString(torDir.resolve("torrc"), "torrc");
        Files.writeString(torDir.resolve("geoip"), "geoip");
        Files.writeString(torDir.resolve("geoip6"), "geoip6");
        Path hiddenservice = Files.createDirectory(torDir.resolve("hiddenservice"));
        Files.writeString(hiddenservice.resolve("hostname"),
                "abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrstuvwx.onion\n");
        Files.writeString(hiddenservice.resolve("hs_ed25519_secret_key"), "secret key");
        Files.writeString(hiddenservice.resolve("hs_ed25519_public_key"), "public key");
    }

    private static Map<String, String> readAllFiles(Path dir) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                files.put(dir.relativize(path).toString(), Files.readString(path));
            }
        }
        return files;
    }
}
