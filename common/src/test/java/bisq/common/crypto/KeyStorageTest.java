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

package bisq.common.crypto;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.IOException;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeyStorageTest {
    @Test
    void createsTheKeyRingForItsOwnerOnly(@TempDir Path keysDir) throws IOException {
        assumeTrue(supportsPosixPermissions());
        Files.setPosixFilePermissions(keysDir, PosixFilePermissions.fromString("rwxr-xr-x"));

        new KeyRing(new KeyStorage(keysDir.toFile()));

        assertEquals("rwx------", permissionsOf(keysDir));
        assertEquals("rw-------", permissionsOf(keysDir.resolve("sig.key")));
        assertEquals("rw-------", permissionsOf(keysDir.resolve("enc.key")));
    }

    // The rolling backups of the keys, which each load writes into the directory with the default mode, are owner-only
    // through the directory
    @Test
    void restrictsAKeyRingOfAnEarlierVersionWhenItIsLoaded(@TempDir Path keysDir) throws IOException {
        assumeTrue(supportsPosixPermissions());
        KeyRing created = new KeyRing(new KeyStorage(keysDir.toFile()));
        Files.setPosixFilePermissions(keysDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.setPosixFilePermissions(keysDir.resolve("sig.key"), PosixFilePermissions.fromString("rw-r--r--"));
        Files.setPosixFilePermissions(keysDir.resolve("enc.key"), PosixFilePermissions.fromString("rw-r--r--"));

        KeyRing loaded = new KeyRing(new KeyStorage(keysDir.toFile()));

        assertArrayEquals(created.getPubKeyRing().toProtoMessage().toByteArray(),
                loaded.getPubKeyRing().toProtoMessage().toByteArray());
        assertEquals("rwx------", permissionsOf(keysDir));
        assertEquals("rw-------", permissionsOf(keysDir.resolve("sig.key")));
        assertEquals("rw-------", permissionsOf(keysDir.resolve("enc.key")));
        try (Stream<Path> backups = Files.walk(keysDir.resolve("backup"))) {
            assertTrue(backups.anyMatch(path -> path.getFileName().toString().endsWith("_sig.key")));
        }
    }

    // Without POSIX permissions, as on Windows, the keys are written and loaded as before
    @Test
    void loadsTheKeyRingWhichItSaved(@TempDir Path keysDir) {
        KeyRing created = new KeyRing(new KeyStorage(keysDir.toFile()));

        KeyRing loaded = new KeyRing(new KeyStorage(keysDir.toFile()));

        assertArrayEquals(created.getSignatureKeyPair().getPrivate().getEncoded(),
                loaded.getSignatureKeyPair().getPrivate().getEncoded());
        assertArrayEquals(created.getEncryptionKeyPair().getPrivate().getEncoded(),
                loaded.getEncryptionKeyPair().getPrivate().getEncoded());
    }

    private static String permissionsOf(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    private static boolean supportsPosixPermissions() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }
}
