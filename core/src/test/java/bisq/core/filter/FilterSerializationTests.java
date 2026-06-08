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

import bisq.common.app.DevEnv;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.ECKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;

import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class FilterSerializationTests {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PublicKey ownerPublicKey;
    private final ECKey signerKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
    private final String signerPubKeyAsHex = HEX.encode(signerKey.getPubKey());

    public FilterSerializationTests() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(Sig.KEY_ALGO, "BC");
        KeyPair ownerKeyPair = keyPairGenerator.generateKeyPair();
        ownerPublicKey = ownerKeyPair.getPublic();
    }

    @Test
    void serializeForHashIncludesFormerExcludedFields() {
        Filter filterWithoutSig = MockFilterFactory.createFilter(ownerPublicKey, signerPubKeyAsHex);

        assertArrayEquals(filterWithoutSig.serialize(), filterWithoutSig.serializeForHash());
    }

    @Test
    void serializeHandlesNullOwnerPubKeyBytes() {
        Filter filter = MockFilterFactory.createFilter(null,
                signerPubKeyAsHex,
                1_700_000_000_000L,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("btc1.onion:8333"),
                List.of("seed1.onion:8001"));

        assertTrue(filter.toProtoMessage().getFilter().getOwnerPubKeyBytes().isEmpty());
        assertNull(Filter.fromProto(filter.toProtoMessage().getFilter()).getOwnerPubKeyBytes());
        assertArrayEquals(filter.serialize(), filter.serializeForHash());
    }

    @Test
    void serializeForHashMatchesProtobufForCanonicalSchema() {
        Filter filter = new Filter(List.of("offer-1", "offer-2"),
                List.of("trading-node-2.onion:8002", "trading-node-1.onion:8001"),
                List.of(new PaymentAccountFilter("SEPA", "getIban", "sha256-v1:abcdef")),
                List.of("USD", "EUR"),
                List.of("SEPA", "ZELLE"),
                List.of("seed2.onion:8002", "seed1.onion:8001"),
                List.of("price2.onion:8002", "price1.onion:8001"),
                true,
                List.of("btc2.onion:8333", "btc1.onion:8333"),
                true,
                "1.9.0",
                "1.9.1",
                List.of("mediator2.onion:8002", "mediator1.onion:8001"),
                List.of("refund2.onion:8002", "refund1.onion:8001"),
                List.of("signer-key-2", "signer-key-1"),
                ownerPublicKey.getEncoded(),
                1_700_000_000_000L,
                "signature-base64",
                signerPubKeyAsHex,
                List.of("dev-key-2", "dev-key-1"),
                true,
                List.of("auto-conf-2", "auto-conf-1"),
                List.of("network-node-2.onion:8002", "network-node-1.onion:8001"),
                true,
                true,
                true,
                -0.0,
                List.of(0, 1, 128, -1),
                1_000L,
                2_000L,
                3_000L,
                4_000L,
                List.of(new PaymentAccountFilter("ZELLE", "getEmail", "sha256-v1:123456")),
                List.of("added-btc-2.onion:8333", "added-btc-1.onion:8333"),
                List.of("added-seed-2.onion:8002", "added-seed-1.onion:8001"),
                "filter-uid",
                true);

        assertArrayEquals(filter.serialize(), filter.serializeForHash());
    }

    @Test
    void fromProtoPreservesNetworkBanOrderForSignedHash() {
        Filter signedFilter = MockFilterFactory.createSignedFilterWithNodeLists(ownerPublicKey,
                signerKey,
                1_700_000_000_000L,
                List.of(),
                List.of(),
                List.of(),
                List.of("node2.onion:8002", "node1.onion:8001"),
                List.of("btc2.onion:8333", "btc1.onion:8333"),
                List.of("seed2.onion:8002", "seed1.onion:8001"));

        Filter roundTrippedFilter = Filter.fromProto(signedFilter.toProtoMessage().getFilter());

        assertEquals(signedFilter.getNodeAddressesBannedFromNetwork(),
                roundTrippedFilter.getNodeAddressesBannedFromNetwork());
        assertArrayEquals(Filter.cloneWithoutSig(signedFilter).serializeForHash(),
                Filter.cloneWithoutSig(roundTrippedFilter).serializeForHash());
        assertEquals(signedFilter.getSignatureAsBase64(), roundTrippedFilter.getSignatureAsBase64());
    }

    @Test
    @SuppressWarnings("deprecation")
    void fromProtoDropsDeprecatedArbitrators() {
        protobuf.Filter proto = protobuf.Filter.newBuilder()
                .addArbitrators("legacy-arbitrator.onion:9999")
                .build();

        Filter roundTrippedFilter = Filter.fromProto(proto);

        assertTrue(roundTrippedFilter.toProtoMessage().getFilter().getArbitratorsList().isEmpty());
    }

    @Test
    void constructorDefensivelyCopiesSignedState() {
        byte[] ownerPubKeyBytes = ownerPublicKey.getEncoded();
        byte[] originalOwnerPubKeyBytes = ownerPubKeyBytes.clone();
        List<String> addedBtcNodes = new ArrayList<>(List.of("btc1.onion:8333"));

        Filter filter = MockFilterFactory.createFilter(ownerPubKeyBytes,
                signerPubKeyAsHex,
                1_700_000_000_000L,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                addedBtcNodes,
                List.of("seed1.onion:8001"));

        ownerPubKeyBytes[0] = (byte) (ownerPubKeyBytes[0] + 1);
        addedBtcNodes.add("btc2.onion:8333");

        assertArrayEquals(originalOwnerPubKeyBytes, filter.getOwnerPubKeyBytes());
        assertEquals(List.of("btc1.onion:8333"), filter.getAddedBtcNodes());

        byte[] returnedOwnerPubKeyBytes = filter.getOwnerPubKeyBytes();
        returnedOwnerPubKeyBytes[0] = (byte) (returnedOwnerPubKeyBytes[0] + 1);

        assertArrayEquals(originalOwnerPubKeyBytes, filter.getOwnerPubKeyBytes());
        assertThrows(UnsupportedOperationException.class,
                () -> filter.getAddedBtcNodes().add("btc3.onion:8333"));
    }
}
