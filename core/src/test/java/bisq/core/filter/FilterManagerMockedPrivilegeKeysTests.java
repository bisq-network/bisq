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

import bisq.core.provider.ProvidersRepository;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.BanFilter;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.ECKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;

import java.nio.file.Path;

import java.io.File;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class FilterManagerMockedPrivilegeKeysTests {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PublicKey ownerPublicKey;
    private final ECKey privilegedDevEcKey;
    private final ECKey secondPrivilegedDevEcKey;

    public FilterManagerMockedPrivilegeKeysTests() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(Sig.KEY_ALGO, "BC");
        KeyPair ownerKeyPair = keyPairGenerator.generateKeyPair();
        ownerPublicKey = ownerKeyPair.getPublic();

        privilegedDevEcKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
        secondPrivilegedDevEcKey = new ECKey();
    }

    @Test
    void testBannedPrivilegedDevKey(@TempDir Path tmpDir,
                                    @Mock P2PService p2PService,
                                    @Mock P2PDataStorage p2pDataStorage) {
        try (MockedStatic<DevEnv> devEnv = Mockito.mockStatic(DevEnv.class)) {

            String privilegedDevPubKeyHex = HEX.encode(privilegedDevEcKey.getPubKey());
            String secondPrivilegedDevPubKeyHex = HEX.encode(secondPrivilegedDevEcKey.getPubKey());
            devEnv.when(DevEnv::getDevPrivilegePubKeys)
                    .thenReturn(List.of(privilegedDevPubKeyHex, secondPrivilegedDevPubKeyHex));

            Config config = mock(Config.class);
            File configFile = tmpDir.resolve("configFile").toFile();
            doReturn(configFile).when(config).getConfigFile();

            FilterManager filterManager = new FilterManager(
                    p2PService,
                    mock(KeyRing.class),
                    mock(User.class),
                    mock(Preferences.class),
                    config,
                    mock(ProvidersRepository.class),
                    mock(BanFilter.class),
                    false,
                    true
            );

            doReturn(p2pDataStorage).when(p2PService).getP2PDataStorage();
            Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> p2pStorageMap = new HashMap<>();
            doReturn(p2pStorageMap).when(p2pDataStorage).getMap();

            // No filter before adding our filter
            assertNull(filterManager.getFilter());

            long creationTime = System.currentTimeMillis();

            List<String> bannedPrivilegedDevKey = List.of(secondPrivilegedDevPubKeyHex);
            Filter firstFilter = TestFilter.createFilter(ownerPublicKey, privilegedDevPubKeyHex,
                    creationTime, bannedPrivilegedDevKey);
            Filter firstFilterWithSig = TestFilter.signFilter(firstFilter, privilegedDevEcKey);


            Filter secondFilterWithSig = TestFilter.createSignedFilter(ownerPublicKey, secondPrivilegedDevEcKey,
                    creationTime + 100);

            assertNotEquals(firstFilterWithSig, secondFilterWithSig);

            p2pStorageMap.put(
                    new P2PDataStorage.ByteArray(new byte[100]),
                    TestFilter.createProtectedStorageEntryForFilter(firstFilterWithSig)
            );

            filterManager.onAllServicesInitialized();

            // Our filter got set
            Filter currentFilter = filterManager.getFilter();
            assertNotNull(currentFilter);
            assertEquals(firstFilterWithSig, currentFilter);

            p2pStorageMap.clear();
            p2pStorageMap.put(
                    new P2PDataStorage.ByteArray(new byte[100]),
                    TestFilter.createProtectedStorageEntryForFilter(secondFilterWithSig)
            );

            filterManager.onAllServicesInitialized();

            // Our filter got set
            currentFilter = filterManager.getFilter();
            assertNotNull(currentFilter);
            assertEquals(firstFilterWithSig, currentFilter);
        }
    }
}
