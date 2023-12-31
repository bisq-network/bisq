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
import java.util.Map;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
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
public class FilterManagerAddFilterToNetworkTests {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> p2pStorageMap;
    private FilterManager filterManager;

    private final PublicKey ownerPublicKey;
    private final String privilegedDevPubKeyHex;
    private final ECKey privilegedDevEcKey;

    public FilterManagerAddFilterToNetworkTests() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(Sig.KEY_ALGO, "BC");
        KeyPair ownerKeyPair = keyPairGenerator.generateKeyPair();
        ownerPublicKey = ownerKeyPair.getPublic();

        privilegedDevEcKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(DevEnv.DEV_PRIVILEGE_PRIV_KEY)));
        privilegedDevPubKeyHex = HEX.encode(privilegedDevEcKey.getPubKey());
    }

    @BeforeEach
    void beforeEach(@TempDir Path tmpDir, @Mock P2PService p2PService, @Mock P2PDataStorage p2pDataStorage) {
        doReturn(p2pDataStorage).when(p2PService).getP2PDataStorage();

        p2pStorageMap = new HashMap<>();
        doReturn(p2pStorageMap).when(p2pDataStorage).getMap();

        Config config = mock(Config.class);
        File configFile = tmpDir.resolve("configFile").toFile();
        doReturn(configFile).when(config).getConfigFile();

        filterManager = new FilterManager(
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
    }

    @Test
    void addFilterWithInvalidPublicKey() {
        // There should exist no filter before we add our filter
        assertNull(filterManager.getFilter());

        Filter filter = TestFilter.createFilter(ownerPublicKey, "invalidPubKeyAsHex");
        p2pStorageMap.put(
                new P2PDataStorage.ByteArray(new byte[100]),
                TestFilter.createProtectedStorageEntryForFilter(filter)
        );

        filterManager.onAllServicesInitialized();

        // FilterManager didn't add our filter
        assertNull(filterManager.getFilter());
    }

    @Test
    void addFilterWithInvalidSignature() {
        // No filter before adding our filter
        assertNull(filterManager.getFilter());

        Filter filter = TestFilter.createFilter(ownerPublicKey, privilegedDevPubKeyHex);
        p2pStorageMap.put(
                new P2PDataStorage.ByteArray(new byte[100]),
                TestFilter.createProtectedStorageEntryForFilter(filter)
        );

        filterManager.onAllServicesInitialized();

        // FilterManager didn't add our filter
        assertNull(filterManager.getFilter());
    }

    @Test
    void publishValidFilter() {
        // No filter before adding our filter
        assertNull(filterManager.getFilter());

        Filter filterWithSig = TestFilter.createSignedFilter(ownerPublicKey, privilegedDevEcKey);
        p2pStorageMap.put(
                new P2PDataStorage.ByteArray(new byte[100]),
                TestFilter.createProtectedStorageEntryForFilter(filterWithSig)
        );

        filterManager.onAllServicesInitialized();

        // Our filter got set
        Filter currentFilter = filterManager.getFilter();
        assertNotNull(currentFilter);
        assertEquals(filterWithSig, currentFilter);
    }

    @Test
    void addTooOldFilter() {
        // No filter before adding our filter
        assertNull(filterManager.getFilter());

        long creationTime = System.currentTimeMillis();
        Filter firstFilterWithSig = TestFilter.createSignedFilter(ownerPublicKey, privilegedDevEcKey, creationTime);
        Filter secondFilterWithSig = TestFilter.createSignedFilter(ownerPublicKey, privilegedDevEcKey,
                creationTime + 100);

        assertNotEquals(firstFilterWithSig, secondFilterWithSig);

        p2pStorageMap.put(
                new P2PDataStorage.ByteArray(new byte[100]),
                TestFilter.createProtectedStorageEntryForFilter(secondFilterWithSig)
        );

        filterManager.onAllServicesInitialized();

        // Our filter got set
        Filter currentFilter = filterManager.getFilter();
        assertNotNull(currentFilter);
        assertEquals(secondFilterWithSig, currentFilter);

        p2pStorageMap.clear();
        p2pStorageMap.put(
                new P2PDataStorage.ByteArray(new byte[100]),
                TestFilter.createProtectedStorageEntryForFilter(firstFilterWithSig)
        );

        filterManager.onAllServicesInitialized();

        // Our filter got set
        currentFilter = filterManager.getFilter();
        assertNotNull(currentFilter);
        assertEquals(secondFilterWithSig, currentFilter);
    }

    @Test
    void addNewerFilter() {
        // No filter before adding our filter
        assertNull(filterManager.getFilter());

        long creationTime = System.currentTimeMillis();
        Filter firstFilterWithSig = TestFilter.createSignedFilter(ownerPublicKey, privilegedDevEcKey, creationTime);
        Filter secondFilterWithSig = TestFilter.createSignedFilter(ownerPublicKey, privilegedDevEcKey,
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
        assertEquals(secondFilterWithSig, currentFilter);
    }
}
