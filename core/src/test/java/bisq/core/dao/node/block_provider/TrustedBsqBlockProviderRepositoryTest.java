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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.block_provider;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrustedBsqBlockProviderRepositoryTest {
    private static final String MAINNET = format("--%s=%s", Config.BASE_CURRENCY_NETWORK, "btc_mainnet");
    private static final String TESTNET = format("--%s=%s", Config.BASE_CURRENCY_NETWORK, "btc_testnet");

    @Test
    public void loadsMainnetSeedNodes() {
        TrustedBsqBlockProviderRepository trustedFullDaoNodes = new TrustedBsqBlockProviderRepository(new Config(MAINNET));
        Collection<TrustedBsqBlockProvider> providers = trustedFullDaoNodes.getTrustedBsqBlockProviders();

        assertEquals(6, providers.size());
        assertEquals(6, providers.stream()
                .filter(node -> node.getRole() == TrustedBsqBlockProvider.Role.SEED)
                .count());
        assertEquals(0, providers.stream()
                .filter(node -> node.getRole() == TrustedBsqBlockProvider.Role.BRIDGE)
                .count());
    }

    @Test
    public void bindsPubKeyToFullNodeAddressAndPort() {
        TrustedBsqBlockProviderRepository trustedFullDaoNodes = new TrustedBsqBlockProviderRepository(new Config(MAINNET));
        NodeAddress seedNodeAddress = new NodeAddress("y6mvobc6tfp7l3rq2rae7hayvkmy35su33cun2mfdbzd4uw536pqtlyd.onion:8000");
        NodeAddress sameHostWrongPort = new NodeAddress("y6mvobc6tfp7l3rq2rae7hayvkmy35su33cun2mfdbzd4uw536pqtlyd.onion:8001");

        assertTrue(isTrusted(trustedFullDaoNodes, seedNodeAddress));
        assertFalse(isTrusted(trustedFullDaoNodes, sameHostWrongPort));
        assertTrue(find(trustedFullDaoNodes, seedNodeAddress)
                .map(TrustedBsqBlockProvider::getEncodedPublicKey)
                .isPresent());
        assertTrue(find(trustedFullDaoNodes, sameHostWrongPort).isEmpty());
    }

    @Test
    public void exposesOperatorMetadataFromResourceEntries() {
        TrustedBsqBlockProviderRepository trustedFullDaoNodes = new TrustedBsqBlockProviderRepository(new Config(MAINNET));
        NodeAddress seedNodeAddress = new NodeAddress("y6mvobc6tfp7l3rq2rae7hayvkmy35su33cun2mfdbzd4uw536pqtlyd.onion:8000");

        TrustedBsqBlockProvider provider = find(trustedFullDaoNodes, seedNodeAddress).orElseThrow();
        assertEquals(TrustedBsqBlockProvider.Role.SEED, provider.getRole());
        assertEquals("jester4042", provider.getOperator());
    }

    @Test
    public void publicKeyBytesAreLoadedFromResources() {
        TrustedBsqBlockProviderRepository trustedFullDaoNodes = new TrustedBsqBlockProviderRepository(new Config(MAINNET));

        assertTrue(trustedFullDaoNodes.getTrustedBsqBlockProviders().stream()
                .allMatch(node -> node.getEncodedPublicKey().length > 0));
    }

    @Test
    public void publicKeyBytesAreDefensiveCopies() {
        TrustedBsqBlockProviderRepository trustedFullDaoNodes = new TrustedBsqBlockProviderRepository(new Config(MAINNET));
        TrustedBsqBlockProvider node = trustedFullDaoNodes.getTrustedBsqBlockProviders().iterator().next();
        byte originalFirstByte = node.getEncodedPublicKey()[0];

        byte[] modified = node.getEncodedPublicKey();
        modified[0] = (byte) (modified[0] + 1);

        assertEquals(originalFirstByte, node.getEncodedPublicKey()[0]);
    }

    @Test
    public void missingNetworkResourceReturnsEmptyProviders() {
        TrustedBsqBlockProviderRepository trustedFullDaoNodes = new TrustedBsqBlockProviderRepository(new Config(TESTNET));

        assertTrue(trustedFullDaoNodes.getTrustedBsqBlockProviders().isEmpty());
    }

    @Test
    public void commandLineProvidersOverrideBundledProvidersWithShellSafeSeparator() throws Exception {
        String customNodeAddress = "custom.onion:8000";
        String signaturePubKeyHex = Utilities.bytesAsHexString(Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic()));
        String trustedNodes = format("--%s=%s@%s",
                Config.BSQ_BLOCK_PROVIDERS,
                customNodeAddress,
                signaturePubKeyHex);
        TrustedBsqBlockProviderRepository trustedFullDaoNodes = new TrustedBsqBlockProviderRepository(new Config(MAINNET, trustedNodes));
        NodeAddress customAddress = new NodeAddress(customNodeAddress);

        assertEquals(1, trustedFullDaoNodes.getTrustedBsqBlockProviders().size());
        assertTrue(isTrusted(trustedFullDaoNodes, customAddress));
        TrustedBsqBlockProvider provider = find(trustedFullDaoNodes, customAddress).orElseThrow();
        assertNull(provider.getRole());
        assertDoesNotThrow(() -> Sig.getPublicKeyFromBytes(provider.getEncodedPublicKey()));
        assertFalse(isTrusted(trustedFullDaoNodes,
                new NodeAddress("y6mvobc6tfp7l3rq2rae7hayvkmy35su33cun2mfdbzd4uw536pqtlyd.onion:8000")));
    }

    @AfterEach
    public void tearDown() {
        new Config();
    }

    private static Optional<TrustedBsqBlockProvider> find(TrustedBsqBlockProviderRepository trustedFullDaoNodes,
                                                          NodeAddress nodeAddress) {
        return trustedFullDaoNodes.getTrustedBsqBlockProviders().stream()
                .filter(provider -> provider.getNodeAddress().equals(nodeAddress))
                .findFirst();
    }

    private static boolean isTrusted(TrustedBsqBlockProviderRepository trustedFullDaoNodes, NodeAddress nodeAddress) {
        return find(trustedFullDaoNodes, nodeAddress).isPresent();
    }
}
