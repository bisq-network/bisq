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

package bisq.seednode;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bisq.env and bitcoin.conf get the Bitcoin RPC password, bisq.env also the private key of the BM oracle. Only the
 * user which reads a file may read it: systemd reads bisq.env as root, bitcoind reads bitcoin.conf as the Bitcoin user.
 */
class SeedNodeInstallScriptTest {
    @ParameterizedTest
    @CsvSource({"seednode/bisq.env, ROOT_USER", "seednode/bitcoin.conf, BITCOIN_USER"})
    void installsTheFilesWithSecretsForTheirReaderOnly(String source, String ownerVariable) throws IOException {
        List<String> installLines = Files.readAllLines(Path.of("install_seednode_debian.sh")).stream()
                .filter(line -> line.contains(" install ") && line.contains(source))
                .toList();

        assertEquals(1, installLines.size());
        String installLine = installLines.get(0);
        assertTrue(installLine.contains(" -m 600 "), installLine);
        assertTrue(installLine.contains(" -o \"${" + ownerVariable + "}\" "), installLine);
    }

    // The node writes its key ring and other files with the default umask into its data directory in this directory
    @Test
    void createsTheHomeOfTheNodeForItsUserOnly() throws IOException {
        List<String> homeLines = Files.readAllLines(Path.of("install_seednode_debian.sh")).stream()
                .filter(line -> line.contains("\"${BISQ_HOME}\"") && !line.contains("${BISQ_REPO_NAME}"))
                .filter(line -> line.contains(" install ") || line.contains(" mkdir ") || line.contains(" chmod "))
                .toList();

        assertEquals(1, homeLines.size());
        String homeLine = homeLines.get(0);
        assertTrue(homeLine.contains(" install -d ") && homeLine.contains(" -m 700 "), homeLine);
        assertTrue(homeLine.contains(" -o \"${BISQ_USER}\" -g \"${BISQ_GROUP}\" "), homeLine);
    }
}
