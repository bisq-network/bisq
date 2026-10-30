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

import bisq.network.tor.bridges.PluggableTransportConfig;
import bisq.network.tor.process.TorDirectories;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * The built-in bridges of the Tor bundle which the node installed in its Tor directory. The node itself passes only the
 * type of the built-in bridges to the Tor library. Versions before the Tor library read only the bridge lines which
 * the Tor network settings store, so the settings store these lines for them.
 */
@Slf4j
public final class BuiltInBridges {
    private BuiltInBridges() {
    }

    /**
     * @return the bridge lines, or an empty list if the bundle is not installed yet
     */
    public static List<String> readInstalled(File torDir, BridgeTransport bridgeTransport) {
        Path pluggableTransportConfigPath = new TorDirectories(torDir.toPath()).getPluggableTransportConfigPath();
        if (!Files.exists(pluggableTransportConfigPath)) {
            return List.of();
        }
        try {
            return PluggableTransportConfig.read(pluggableTransportConfigPath)
                    .findBuiltInBridges(bridgeTransport.getBuiltInBridgesName())
                    .orElse(List.of());
        } catch (RuntimeException e) {
            log.warn("Could not read the built-in bridges of the Tor bundle", e);
            return List.of();
        }
    }
}
