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

package bisq.core.dao.monitoring.serialization;

import bisq.core.dao.state.model.DaoState;

import bisq.common.crypto.Hash;

import com.google.protobuf.CodedInputStream;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks {@link LegacyProtobufDaoStateSerializer} to the real mainnet bytes
 * that the network has been hashing since genesis.
 *
 * <p>Bisq ships a {@code DaoStateStore_BTC_MAINNET} resource (a serialized
 * {@code PersistableEnvelope} wrapping a {@code DaoStateStore}) with every
 * release. It contains the full {@code DaoState} as of the snapshot height
 * plus the {@code DaoStateHash} chain — the actual hashes the network agreed
 * on. This test loads that store, recomputes the hash for the snapshot's
 * last block via {@link LegacyProtobufDaoStateSerializer}, and asserts byte
 * equality against the stored hash.
 *
 * <p>If {@link LegacyProtobufDaoStateSerializer} (using
 * {@link PinnedHashMap}) produces different bytes from what the network
 * produced under {@code java.util.HashMap}, the recomputed hash will not
 * match and the test fails.
 *
 * <p>The shipped file is large (~70 MB) so this test is gated on its
 * presence at the expected repo-relative path. It runs in normal Gradle
 * test runs since the resource is checked in; if the path moves the
 * test self-skips rather than reporting a misleading failure.
 */
public class LegacyByteEqualityTest {

    private static final Path SHIPPED_STORE = Paths.get(
            "..", "p2p", "src", "main", "resources", "DaoStateStore_BTC_MAINNET");
    private static final Path SHIPPED_BLOCKS_DIR = Paths.get(
            "..", "p2p", "src", "main", "resources", "BsqBlocks_BTC_MAINNET");
    private static final int BLOCKS_PER_BUCKET = 1000;

    // Hardcoded ground truth: the DAO state hash the mainnet network agreed on
    // at block 949481 (the last height covered by the shipped DaoStateStore at
    // the time this test was written). Verified independently via the Bisq GUI
    // "DAO state monitor" view. Asserting against this constant — not against
    // the shipped store's own copy — defends against silent tampering of the
    // bundled DaoStateStore_BTC_MAINNET resource: if someone swapped the
    // shipped file, the test would still demand the right hash.
    private static final int GROUND_TRUTH_HEIGHT = 949481;
    private static final String GROUND_TRUTH_HASH_HEX =
            "64d134e8b29bf9dfba4b6f395f21032174bc6496";

    static boolean shippedStoreExists() {
        return SHIPPED_STORE.toFile().isFile() && SHIPPED_BLOCKS_DIR.toFile().isDirectory();
    }

    private static protobuf.PersistableEnvelope readEnvelope(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            CodedInputStream cis = CodedInputStream.newInstance(fis);
            cis.setSizeLimit(Integer.MAX_VALUE);
            cis.setRecursionLimit(Integer.MAX_VALUE);
            cis.readRawVarint32(); // delimited size prefix
            return protobuf.PersistableEnvelope.parseFrom(cis);
        }
    }

    /**
     * Locate the {@link protobuf.BaseBlock} for {@code chainHeight} inside the
     * shipped {@code BsqBlocks_*} chunks. Returns {@link Optional#empty()} if
     * the bundled chunks do not cover that height (snapshot newer than
     * shipped blocks).
     */
    private static Optional<protobuf.BaseBlock> findShippedBlockAt(int chainHeight) throws Exception {
        // BlocksPersistence writes a block at height H into the bucket whose
        // closing height is the next multiple of BLOCKS_PER_BUCKET that is
        // >= H, i.e. ceil(H / BLOCKS_PER_BUCKET). The naive H/N+1 form is
        // wrong when H is exactly N*BLOCKS_PER_BUCKET.
        int bucketIndex = (chainHeight + BLOCKS_PER_BUCKET - 1) / BLOCKS_PER_BUCKET;
        int first = (bucketIndex - 1) * BLOCKS_PER_BUCKET + 1;
        int last = bucketIndex * BLOCKS_PER_BUCKET;
        File chunk = SHIPPED_BLOCKS_DIR.resolve("BsqBlocks_" + first + "-" + last).toFile();
        if (!chunk.isFile()) {
            return Optional.empty();
        }
        protobuf.PersistableEnvelope env = readEnvelope(chunk);
        if (!env.hasBsqBlockStore()) {
            return Optional.empty();
        }
        for (protobuf.BaseBlock baseBlock : env.getBsqBlockStore().getBlocksList()) {
            if (baseBlock.getHeight() == chainHeight) {
                return Optional.of(baseBlock);
            }
        }
        return Optional.empty();
    }

    @Test
    @EnabledIf("shippedStoreExists")
    public void recomputedHashOfLastBlockMatchesShippedHash() throws Exception {
        protobuf.PersistableEnvelope env = readEnvelope(SHIPPED_STORE.toFile());
        assertTrue(env.hasDaoStateStore(), "Shipped envelope must wrap a DaoStateStore");
        protobuf.DaoStateStore storeProto = env.getDaoStateStore();

        List<protobuf.DaoStateHash> hashChain = storeProto.getDaoStateHashList();
        assertFalse(hashChain.isEmpty(), "Shipped DaoStateStore has no hash chain");

        // The shipped DaoStateStore_BTC_MAINNET does not inline blocks; those
        // live in the chunked BsqBlocks_BTC_MAINNET/BsqBlocks_<from>-<to>
        // resources. LegacyProtobufDaoStateSerializer only needs the LAST
        // block (DaoState.getLastBlock()), so stitch in just that one.
        protobuf.DaoState stateProto = storeProto.getDaoState();
        int chainHeight = stateProto.getChainHeight();
        assertTrue(chainHeight >= GROUND_TRUTH_HEIGHT,
                "Shipped DaoStateStore is older than the hardcoded ground-truth height " + GROUND_TRUTH_HEIGHT
                        + "; update the ground-truth constants from the new bundled snapshot");

        Optional<protobuf.BaseBlock> baseBlockOpt = findShippedBlockAt(GROUND_TRUTH_HEIGHT);
        assertTrue(baseBlockOpt.isPresent(),
                "Shipped BsqBlocks_BTC_MAINNET does not contain height " + GROUND_TRUTH_HEIGHT);
        LinkedList<bisq.core.dao.state.model.blockchain.Block> blocks = new LinkedList<>();
        blocks.add(bisq.core.dao.state.model.blockchain.Block.fromProto(baseBlockOpt.get()));

        DaoState daoState = DaoState.fromProto(stateProto, blocks);
        // If the shipped snapshot is ahead of GROUND_TRUTH_HEIGHT, the maps
        // hold entries added by later blocks and the hash will not match. We
        // assert chainHeight == GROUND_TRUTH_HEIGHT to keep the test honest:
        // when the bundled snapshot moves forward, bump both constants and
        // re-verify the new hash against the Bisq GUI's DAO state monitor.
        assertTrue(chainHeight == GROUND_TRUTH_HEIGHT,
                "Shipped chainHeight=" + chainHeight + " no longer matches ground-truth height "
                        + GROUND_TRUTH_HEIGHT + "; refresh GROUND_TRUTH_HEIGHT and GROUND_TRUTH_HASH_HEX");

        // Walk the chain to find the entry at GROUND_TRUTH_HEIGHT and the one
        // immediately before (its hash becomes prevHash for the recomputation).
        protobuf.DaoStateHash targetHashProto = null;
        protobuf.DaoStateHash prevHashProto = null;
        for (int i = 0; i < hashChain.size(); i++) {
            if (hashChain.get(i).getHeight() == GROUND_TRUTH_HEIGHT) {
                targetHashProto = hashChain.get(i);
                prevHashProto = i > 0 ? hashChain.get(i - 1) : null;
                break;
            }
        }
        assertTrue(targetHashProto != null,
                "DaoStateHash chain has no entry for height " + GROUND_TRUTH_HEIGHT);

        // First: assert that the shipped store's own copy of the hash matches
        // the network-agreed value. If this fails, the bundled file has been
        // tampered with — the serializer can be correct and the test still
        // catches the regression.
        byte[] groundTruth = hexDecode(GROUND_TRUTH_HASH_HEX);
        assertArrayEquals(groundTruth, targetHashProto.getHash().toByteArray(),
                "Shipped DaoStateStore_BTC_MAINNET hash for height " + GROUND_TRUTH_HEIGHT
                        + " does not match the network-agreed value " + GROUND_TRUTH_HASH_HEX);

        // Second: recompute the hash from scratch and assert it equals the
        // hardcoded network-agreed value. This is the real proof —
        // LegacyProtobufDaoStateSerializer (with PinnedHashMap underneath)
        // reproduces the exact bytes the network hashed.
        byte[] prevHash = prevHashProto == null
                ? new byte[0]
                : prevHashProto.getHash().toByteArray();
        byte[] stateBytes = new LegacyProtobufDaoStateSerializer().serialize(daoState);
        byte[] combined = ArrayUtils.addAll(prevHash, stateBytes);
        byte[] recomputed = Hash.getSha256Ripemd160hash(combined);

        assertArrayEquals(groundTruth, recomputed,
                "LegacyProtobufDaoStateSerializer must reproduce the network-agreed hash "
                        + GROUND_TRUTH_HASH_HEX + " at height " + GROUND_TRUTH_HEIGHT
                        + "; got " + hexEncode(recomputed));
    }

    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
