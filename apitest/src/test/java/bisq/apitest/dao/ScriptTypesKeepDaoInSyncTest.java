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

package bisq.apitest.dao;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full DAO nodes map the script type of every output that Bitcoin Core reports for a
 * block, also of transactions without BSQ. A type the mapping rejects stops the parsing
 * of the block (see docs/specifications/dao/bitcoind-script-types.md).
 *
 * <p>Mines one block each whose coinbase pays to a taproot address, a pay-to-anchor
 * output, a witness program of version 2 and a bare OP_TRUE script. Bitcoin Core reports
 * them as {@code witness_v1_taproot}, {@code anchor}, {@code witness_unknown} and
 * {@code nonstandard}. Alice, Bob and the seed node must parse each block and stay in
 * sync. These types are known to v1.10.11 as well, so this is a regression guard for the
 * mapping, not a test of the fallback for types newer than the stack's Bitcoin Core.
 *
 * <p>Runs on a freshly reset stack (@Tag("freshstack")), so a parse failure does not
 * break the shared-stack tests.
 */
@Slf4j
@Tag("freshstack")
public class ScriptTypesKeepDaoInSyncTest extends DaoTestBase {
    private static final long STAY_IN_SYNC_MS = 15_000;
    private static final String P2A_SCRIPT = "51024e73";
    private static final String WITNESS_V2_SCRIPT = "5220" + "01".repeat(32);
    private static final Pattern BLOCK_HASH = Pattern.compile("[0-9a-f]{64}");

    @Test
    public void blocksWithUnusualOutputScriptsKeepTheDaoInSync() {
        assertInSync("baseline (fresh stack)");

        String taprootAddress = dao.runBitcoinCli("getnewaddress", "", "bech32m").trim();
        mineCoinbaseTo("generatetoaddress", taprootAddress, "witness_v1_taproot");
        mineCoinbaseTo("generatetoaddress", addressOf(P2A_SCRIPT), "anchor");
        mineCoinbaseTo("generatetoaddress", addressOf(WITNESS_V2_SCRIPT), "witness_unknown");
        mineCoinbaseTo("generatetodescriptor", descriptorWithChecksum("raw(51)"), "nonstandard");

        // A node that failed on one of these blocks would be behind or report a conflict
        // with the seed node once the next block arrives.
        dao.generateBlocks(1);
        assertInSync("blocks with unusual output scripts");
        assertStaysInSync("blocks with unusual output scripts");
    }

    private void mineCoinbaseTo(String rpc, String target, String expectedScriptType) {
        String blockHash = firstMatch(BLOCK_HASH, dao.runBitcoinCli(rpc, "1", target), rpc + " " + target);
        String block = dao.runBitcoinCli("getblock", blockHash, "2");
        assertTrue(block.contains("\"type\": \"" + expectedScriptType + "\""),
                "block " + blockHash + " has no output of type " + expectedScriptType);

        int height = dao.chainTip();
        dao.awaitChainHeight(alice, height, "alice");
        dao.awaitChainHeight(bob, height, "bob");
        log.info("Block {} at height {} with a {} coinbase output parsed", blockHash, height, expectedScriptType);
    }

    private String addressOf(String scriptHex) {
        String decoded = dao.runBitcoinCli("decodescript", scriptHex);
        return firstMatch(Pattern.compile("\"address\": \"([^\"]+)\""), decoded, "address of " + scriptHex);
    }

    private String descriptorWithChecksum(String descriptor) {
        String info = dao.runBitcoinCli("getdescriptorinfo", descriptor);
        String checksum = firstMatch(Pattern.compile("\"checksum\": \"([^\"]+)\""), info, "checksum of " + descriptor);
        return descriptor + "#" + checksum;
    }

    private static String firstMatch(Pattern pattern, String text, String what) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("no " + what + " in: " + text);
        }
        return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
    }

    /** getDaoStatus is true until a differing seed hash arrived, so watch it for a while. */
    private void assertStaysInSync(String stage) {
        long end = System.currentTimeMillis() + STAY_IN_SYNC_MS;
        while (System.currentTimeMillis() < end) {
            assertTrue(alice.getDaoStatus(), "alice out of sync with seed node after " + stage);
            assertTrue(bob.getDaoStatus(), "bob out of sync with seed node after " + stage);
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void assertInSync(String stage) {
        DaoTestUtils.await(alice::getDaoStatus, 60_000,
                "alice DAO state in sync with seed node after " + stage);
        DaoTestUtils.await(bob::getDaoStatus, 60_000,
                "bob DAO state in sync with seed node after " + stage);
    }
}
