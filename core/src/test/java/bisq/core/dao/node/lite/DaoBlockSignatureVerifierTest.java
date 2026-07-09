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

package bisq.core.dao.node.lite;

import bisq.core.dao.node.block_provider.TrustedBsqBlockProvider;
import bisq.core.dao.node.block_provider.TrustedBsqBlockProviderRepository;
import bisq.core.dao.node.full.DaoBlockSignatureHash;
import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.messages.DaoBlockSignature;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.SignedRawBlock;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import java.security.KeyPair;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DaoBlockSignatureVerifierTest {
    private static final String MAINNET = format("--%s=%s", Config.BASE_CURRENCY_NETWORK, "btc_mainnet");
    private static final NodeAddress SIGNER_NODE_ADDRESS = new NodeAddress("trusted.onion:8000");
    private static final NodeAddress SECOND_SIGNER_NODE_ADDRESS = new NodeAddress("trusted2.onion:8000");
    private static final NodeAddress UNTRUSTED_NODE_ADDRESS = new NodeAddress("untrusted.onion:8000");

    @Test
    public void acceptsSignatureFromTrustedNode() throws Exception {
        KeyPair keyPair = Sig.generateKeyPair();
        RawBlock rawBlock = createRawBlock("block-hash");
        SignedRawBlock signedRawBlock = createSignedRawBlock(rawBlock, SIGNER_NODE_ADDRESS, keyPair);
        DaoBlockSignatureVerifier verifier = new DaoBlockSignatureVerifier(new TestTrustedBsqBlockProviderRepository(
                Map.of(SIGNER_NODE_ADDRESS, Sig.getPublicKeyBytes(keyPair.getPublic()))));

        assertTrue(verifier.isValid(signedRawBlock));
    }

    @Test
    public void rejectsSignatureFromUntrustedNode() throws Exception {
        KeyPair keyPair = Sig.generateKeyPair();
        RawBlock rawBlock = createRawBlock("block-hash");
        SignedRawBlock signedRawBlock = createSignedRawBlock(rawBlock, UNTRUSTED_NODE_ADDRESS, keyPair);
        DaoBlockSignatureVerifier verifier = new DaoBlockSignatureVerifier(new TestTrustedBsqBlockProviderRepository(
                Map.of(SIGNER_NODE_ADDRESS, Sig.getPublicKeyBytes(keyPair.getPublic()))));

        assertFalse(verifier.isValid(signedRawBlock));
    }

    @Test
    public void rejectsTamperedBlock() throws Exception {
        KeyPair keyPair = Sig.generateKeyPair();
        RawBlock rawBlock = createRawBlock("block-hash");
        SignedRawBlock signedRawBlock = createSignedRawBlock(rawBlock, SIGNER_NODE_ADDRESS, keyPair);
        SignedRawBlock tamperedSignedRawBlock = new SignedRawBlock(createRawBlock("tampered-block-hash"),
                signedRawBlock.getSignature());
        DaoBlockSignatureVerifier verifier = new DaoBlockSignatureVerifier(new TestTrustedBsqBlockProviderRepository(
                Map.of(SIGNER_NODE_ADDRESS, Sig.getPublicKeyBytes(keyPair.getPublic()))));

        assertFalse(verifier.isValid(tamperedSignedRawBlock));
    }

    @Test
    public void getBlocksResponseRoundTripsSignedBlocks() throws Exception {
        KeyPair keyPair = Sig.generateKeyPair();
        RawBlock rawBlock = createRawBlock("block-hash");
        SignedRawBlock signedRawBlock = createSignedRawBlock(rawBlock, SIGNER_NODE_ADDRESS, keyPair);
        GetBlocksResponse response = GetBlocksResponse.forSignedBlocks(List.of(signedRawBlock), 42);

        GetBlocksResponse roundTripped = (GetBlocksResponse) GetBlocksResponse.fromProto(
                response.toProtoNetworkEnvelope().getGetBlocksResponse(),
                1);

        assertTrue(roundTripped.getBlocks().isEmpty());
        assertEquals(1, roundTripped.getSignedRawBlocks().size());
        assertEquals(SIGNER_NODE_ADDRESS, roundTripped.getSignedRawBlocks().get(0).getSignature().getSignerNodeAddress());
        assertEquals(rawBlock, roundTripped.getSignedRawBlocks().get(0).getBlock());
    }

    @Test
    public void getBlocksResponseRoundTripsUnsignedBlocks() {
        RawBlock rawBlock = createRawBlock("block-hash");
        GetBlocksResponse response = GetBlocksResponse.forUnsignedBlocks(List.of(rawBlock), 42);

        GetBlocksResponse roundTripped = (GetBlocksResponse) GetBlocksResponse.fromProto(
                response.toProtoNetworkEnvelope().getGetBlocksResponse(),
                1);

        assertEquals(1, roundTripped.getBlocks().size());
        assertTrue(roundTripped.getSignedRawBlocks().isEmpty());
        assertEquals(rawBlock, roundTripped.getBlocks().get(0));
    }

    private static SignedRawBlock createSignedRawBlock(RawBlock rawBlock,
                                                       NodeAddress signerNodeAddress,
                                                       KeyPair keyPair) throws Exception {
        byte[] signature = Sig.sign(keyPair.getPrivate(), DaoBlockSignatureHash.getHash(rawBlock));
        return new SignedRawBlock(rawBlock, new DaoBlockSignature(signerNodeAddress, signature));
    }

    private static RawBlock createRawBlock(String hash) {
        return createRawBlock(hash, null);
    }

    private static RawBlock createRawBlock(String hash, String txId) {
        return createRawBlock(100, hash, txId);
    }

    private static RawBlock createRawBlock(int height, String hash, String txId) {
        protobuf.RawBlock.Builder rawBlockBuilder = protobuf.RawBlock.newBuilder();
        Optional.ofNullable(txId).ifPresent(id -> rawBlockBuilder.addRawTxs(protobuf.BaseTx.newBuilder()
                .setTxVersion("1")
                .setId(id)
                .setBlockHeight(height)
                .setBlockHash(hash)
                .setTime(1234)
                .setRawTx(protobuf.RawTx.newBuilder())
                .build()));
        protobuf.BaseBlock proto = protobuf.BaseBlock.newBuilder()
                .setHeight(height)
                .setTime(1234)
                .setHash(hash)
                .setPreviousBlockHash("previous-block-hash")
                .setRawBlock(rawBlockBuilder)
                .build();
        return RawBlock.fromProto(proto);
    }

    @AfterEach
    public void tearDown() {
        new Config();
    }

    private static class TestTrustedBsqBlockProviderRepository extends TrustedBsqBlockProviderRepository {
        private final Collection<TrustedBsqBlockProvider> trustedBsqBlockProviders;

        private TestTrustedBsqBlockProviderRepository(Map<NodeAddress, byte[]> signaturePubKeyBytesByNodeAddress) {
            super(new Config(MAINNET));
            this.trustedBsqBlockProviders = signaturePubKeyBytesByNodeAddress.entrySet().stream()
                    .map(entry -> TrustedBsqBlockProvider.fromConfig(format("%s|%s",
                            entry.getKey().getFullAddress(),
                            Utilities.bytesAsHexString(entry.getValue()))))
                    .collect(Collectors.toSet());
        }

        @Override
        public Collection<TrustedBsqBlockProvider> getTrustedBsqBlockProviders() {
            return trustedBsqBlockProviders;
        }
    }
}
