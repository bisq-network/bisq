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

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TorSetupTest {
    @Test
    void cleanupKeepsTheOnionServiceKeyAndTheExternalTorConfig(@TempDir Path torDir) throws IOException {
        Files.createDirectories(torDir.resolve("hiddenservice"));
        Files.writeString(torDir.resolve("hiddenservice").resolve("private_key"), "key");
        Files.writeString(torDir.resolve("external_tor.config"), "UseExternalTor 1");
        Files.writeString(torDir.resolve("torrc"), "DisableNetwork 1");
        Files.writeString(torDir.resolve("cached-microdescs"), "");
        Files.createDirectories(torDir.resolve("pluggable_transports"));
        Files.writeString(torDir.resolve("pluggable_transports").resolve("lyrebird"), "");
        AtomicBoolean completed = new AtomicBoolean();

        new TorSetup(torDir.toFile()).cleanupTorFiles(() -> completed.set(true), errorMessage -> fail(errorMessage));

        assertTrue(completed.get());
        try (Stream<Path> files = Files.list(torDir)) {
            Set<String> remainingFileNames = files.map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("hiddenservice", "external_tor.config"), remainingFileNames);
        }
        assertEquals("key", Files.readString(torDir.resolve("hiddenservice").resolve("private_key")));
        assertEquals("UseExternalTor 1", Files.readString(torDir.resolve("external_tor.config")));
    }
}
