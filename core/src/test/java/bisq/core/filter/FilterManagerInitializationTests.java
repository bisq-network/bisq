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

package bisq.core.filter;

import bisq.core.locale.Res;
import bisq.core.provider.PriceFeedNodeAddressProvider;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.BanFilter;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mockito.MockedStatic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class FilterManagerInitializationTests {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void networkBansIncludeDenyListBeforeNetworkFilterArrives() {
        Properties properties = new Properties();
        properties.setProperty("nodeAddressesBannedFromNetwork", "blocked.onion:9999");
        var filterManager = new FilterManager(
                mock(P2PService.class),
                mock(KeyRing.class),
                mock(User.class),
                mock(Preferences.class),
                DenyList.fromProperties(properties),
                mock(Config.class),
                mock(PriceFeedNodeAddressProvider.class),
                mock(BanFilter.class),
                mock(PriceFeedService.class),
                false,
                true
        );

        assertTrue(filterManager.isNodeAddressBannedFromNetwork(new NodeAddress("blocked.onion:9999")));
        assertFalse(filterManager.isNodeAddressBannedFromNetwork(new NodeAddress("allowed.onion:9999")));
    }

    @Test
    void onAllServicesInitializedNoFilterMainnet() {
        P2PService p2PService = mock(P2PService.class);
        var filterManager = new FilterManager(
                p2PService,
                mock(KeyRing.class),
                mock(User.class),
                mock(Preferences.class),
                DenyList.empty(),
                mock(Config.class),
                mock(PriceFeedNodeAddressProvider.class),
                mock(BanFilter.class),
                mock(PriceFeedService.class),
                false,
                true
        );

        var p2pStorageMap = new HashMap<>();
        P2PDataStorage p2pDataStorage = mock(P2PDataStorage.class);

        doReturn(p2pStorageMap).when(p2pDataStorage).getMap();
        doReturn(p2pDataStorage).when(p2PService).getP2PDataStorage();

        final var warningHandlerTriggered = new AtomicBoolean();
        try (MockedStatic<Res> mocked = mockStatic(Res.class)) {
            mocked.when(() -> Res.get("popup.warning.noFilter"))
                    .thenReturn("No filter.");

            filterManager.setFilterWarningHandler(errorMessage -> {
                if (errorMessage.equals(Res.get("popup.warning.noFilter"))) {
                    warningHandlerTriggered.set(true);
                }
            });

            filterManager.onAllServicesInitialized();
        }

        assertTrue(warningHandlerTriggered.get());
    }

    @Test
    void onAllServicesInitializedNoFilterMainnetIgnoreNetworkFilter(@TempDir Path tmpDir) throws IOException {
        P2PService p2PService = mock(P2PService.class);
        Config config = mock(Config.class);
        Path configFilePath = tmpDir.resolve("configFile");
        File configFile = configFilePath.toFile();
        Files.writeString(configFilePath,
                "bannedBtcNodes=btc.onion:8333\n" +
                        "filterProvidedBtcNodes=provided-btc.onion:8333\n" +
                        "bannedSeedNodes=seed.onion:9999\n" +
                        "filterProvidedSeedNodes=provided-seed.onion:9999\n" +
                        "bannedPriceRelayNodes=price\n" +
                        "otherOption=keep\n");
        doReturn(configFile).when(config).getConfigFile();

        var filterManager = new FilterManager(
                p2PService,
                mock(KeyRing.class),
                mock(User.class),
                mock(Preferences.class),
                DenyList.empty(),
                config,
                mock(PriceFeedNodeAddressProvider.class),
                mock(BanFilter.class),
                mock(PriceFeedService.class),
                true,
                true
        );

        final var warningHandlerTriggered = new AtomicBoolean();
        filterManager.setFilterWarningHandler(errorMessage -> warningHandlerTriggered.set(true));

        filterManager.onAllServicesInitialized();

        assertFalse(warningHandlerTriggered.get());
        String configContent = Files.readString(configFilePath);
        assertFalse(configContent.contains("bannedBtcNodes"));
        assertFalse(configContent.contains("filterProvidedBtcNodes"));
        assertFalse(configContent.contains("bannedSeedNodes"));
        assertFalse(configContent.contains("filterProvidedSeedNodes"));
        assertFalse(configContent.contains("bannedPriceRelayNodes"));
        assertTrue(configContent.contains("otherOption=keep"));
    }
}
