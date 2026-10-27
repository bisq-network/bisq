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


package bisq.network.p2p.network;

import bisq.network.tor.process.TorDirectories;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeSettingsTest {
    @Test
    void readsTheStoredTransportOfEarlierVersions() {
        // Earlier versions stored the ordinal of OBFS_4, OBFS_3, MEEK_AMAZON and MEEK_AZURE.
        assertEquals(BridgeTransport.OBFS_4, BridgeTransport.fromPreferences(0));
        assertEquals(BridgeTransport.SNOWFLAKE, BridgeTransport.fromPreferences(1));
        assertEquals(BridgeTransport.MEEK, BridgeTransport.fromPreferences(2));
        assertEquals(BridgeTransport.MEEK, BridgeTransport.fromPreferences(3));
        assertEquals(BridgeTransport.OBFS_4, BridgeTransport.fromPreferences(-1));
        // The new values are valid values of earlier versions, which read them after a downgrade.
        for (BridgeTransport transport : BridgeTransport.values()) {
            assertTrue(transport.ordinal() < 4);
            assertEquals(transport, BridgeTransport.fromPreferences(transport.ordinal()));
        }
        assertEquals(BridgeOption.CUSTOM, BridgeOption.fromPreferences(2));
        assertEquals(BridgeOption.NONE, BridgeOption.fromPreferences(7));
    }

    @Test
    void readsTheBuiltInBridgesOfTheInstalledBundle(@TempDir File torDir) throws IOException {
        assertEquals(List.of(), BuiltInBridges.readInstalled(torDir, BridgeTransport.OBFS_4));

        // The configuration of the transports in the layout of the Tor library
        Path ptConfigPath = new TorDirectories(torDir.toPath()).getPluggableTransportConfigPath();
        Files.createDirectories(ptConfigPath.getParent());
        Files.writeString(ptConfigPath, "{\"pluggableTransports\": {}, " +
                "\"bridges\": {\"snowflake\": [\"snowflake 192.0.2.3:80 ABC\"]}}");

        assertEquals(List.of("snowflake 192.0.2.3:80 ABC"),
                BuiltInBridges.readInstalled(torDir, BridgeTransport.SNOWFLAKE));
        assertEquals(List.of(), BuiltInBridges.readInstalled(torDir, BridgeTransport.MEEK));
    }
}
