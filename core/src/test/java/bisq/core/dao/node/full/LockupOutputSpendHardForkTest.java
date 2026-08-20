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

package bisq.core.dao.node.full;

import bisq.core.dao.DaoHardFork;
import bisq.core.dao.node.parser.exceptions.InvalidParsingConditionException;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;

import com.google.common.collect.ImmutableList;

import java.io.IOException;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same transaction shapes as {@link LockupOutputSpendTest}, but from the hard fork 3 activation height on, where
 * a lockup output may only be spent by a formal unlock tx. Every other spend is invalid, so the collateral is burnt
 * instead of returned to the owner.
 */
public class LockupOutputSpendHardForkTest extends LockupOutputSpendTestBase {
    // The first height at which the rule applies on mainnet, which is the network the tests run on by default.
    private static final int ACTIVATION_HEIGHT = DaoHardFork.getHardFork3ActivationHeight();

    @BeforeEach
    public void setup() {
        startChainAt(ACTIVATION_HEIGHT);
    }

    @Test
    public void theRuleAppliesFromItsHeightOnAndNotBefore() throws IOException {
        // One contiguous chain across the activation height, with the same spend shape on either side of it.
        startChainAt(ACTIVATION_HEIGHT - 4);

        addLockupTx("lockupA", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        parseBlock(rawTx("spendBefore",
                List.of(new TxInput("lockupA", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT / 2, "spendBefore", null),
                        output(1, LOCKUP_AMOUNT / 2, "spendBefore", null))));
        Tx before = getTx("spendBefore");
        assertEquals(ACTIVATION_HEIGHT - 2, before.getBlockHeight());
        assertEquals(TxType.TRANSFER_BSQ, before.getTxType());
        assertEquals(0, before.getBurntBsq());

        addLockupTx("lockupB", "lockupA", 1, GENESIS_SUPPLY - LOCKUP_AMOUNT);
        parseBlock(rawTx("spendAfter",
                List.of(new TxInput("lockupB", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT / 2, "spendAfter", null),
                        output(1, LOCKUP_AMOUNT / 2, "spendAfter", null))));
        assertEquals(ACTIVATION_HEIGHT, getTx("spendAfter").getBlockHeight());
        assertCollateralBurnt("spendAfter", LOCKUP_AMOUNT, "lockupB");
    }

    @Test
    public void theHonestPathIsUnaffected() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        parseBlock(rawTx("unlockTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT, "unlockTx", null))));

        Tx unlockTx = getTx("unlockTx");
        assertEquals(TxType.UNLOCK, unlockTx.getTxType());
        assertEquals(TxOutputType.UNLOCK_OUTPUT, unlockTx.getTxOutputs().get(0).getTxOutputType());
        assertEquals(0, unlockTx.getBurntBsq());
        assertFalse(daoStateService.isTxOutputSpendable(new TxOutputKey("unlockTx", 0)));
        assertTrue(canConfiscate("lockupTx"));
    }

    @Test
    public void aCanonicalUnlockMayContainAnUnrelatedBtcInputAndChangeOutput() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        parseBlock(rawTx("unlockTx",
                List.of(new TxInput("btcFeeInput", 0, null), new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT, "unlockTx", null),
                        output(1, 10_000, "unlockTx", null))));

        Tx unlockTx = getTx("unlockTx");
        assertEquals(TxType.UNLOCK, unlockTx.getTxType());
        assertEquals(TxOutputType.UNLOCK_OUTPUT, unlockTx.getTxOutputs().get(0).getTxOutputType());
        assertEquals(TxOutputType.BTC_OUTPUT, unlockTx.getTxOutputs().get(1).getTxOutputType());
        assertEquals(0, unlockTx.getBurntBsq());
    }

    @Test
    public void shapeA_splittingTheLockupValueIsRejected() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT / 2, "spendTx", null),
                        output(1, LOCKUP_AMOUNT / 2, "spendTx", null))));

        assertCollateralBurnt("spendTx", LOCKUP_AMOUNT, "lockupTx");
    }

    @Test
    public void shapeB_anExtraBsqInputIsRejected() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        long change = GENESIS_SUPPLY - LOCKUP_AMOUNT;

        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupTx", 0, null), new TxInput("lockupTx", 1, null)),
                List.of(output(0, LOCKUP_AMOUNT + change, "spendTx", null))));

        // The unrelated BSQ input is burnt with the rest. That is the same treatment every other invalid tx gets,
        // and it can only happen in a hand built tx which deliberately spends a lockup output.
        assertCollateralBurnt("spendTx", LOCKUP_AMOUNT + change, "lockupTx");
    }

    @Test
    public void shapeC_rollingABondForwardWithoutUnlockingIsRejected() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        parseBlock(rawTx("chainTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, 300_000, "chainTx", null),
                        output(1, 200_000, "chainTx", null),
                        output(2, 0, "chainTx", lockupOpReturn()))));

        assertCollateralBurnt("chainTx", LOCKUP_AMOUNT, "lockupTx");
        // In particular no new bond was created from the old collateral.
        assertTrue(getTx("chainTx").getTxOutputs().stream()
                .noneMatch(o -> o.getTxOutputType() == TxOutputType.LOCKUP_OUTPUT));
    }

    @Test
    public void shapeD_spendingTwoLockupOutputsInOneTxIsRejected() throws IOException {
        addLockupTx("lockupA", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        addLockupTx("lockupB", "lockupA", 1, GENESIS_SUPPLY - LOCKUP_AMOUNT);

        long total = LOCKUP_AMOUNT + LOCKUP_AMOUNT;
        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupA", 0, null), new TxInput("lockupB", 0, null)),
                List.of(output(0, total / 2, "spendTx", null),
                        output(1, total / 2, "spendTx", null))));

        assertCollateralBurnt("spendTx", total, "lockupA", "lockupB");
    }

    @Test
    public void aLockupCannotBeSpentIllegallyLaterInTheSameBlock() throws IOException {
        RawTx lockup = rawTx("lockupTx",
                List.of(new TxInput(GENESIS_TX_ID, 0, null)),
                List.of(output(0, LOCKUP_AMOUNT, "lockupTx", null),
                        output(1, GENESIS_SUPPLY - LOCKUP_AMOUNT, "lockupTx", null),
                        output(2, 0, "lockupTx", lockupOpReturn())));
        RawTx spend = rawTx("spendTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT / 2, "spendTx", null),
                        output(1, LOCKUP_AMOUNT / 2, "spendTx", null)));

        parseBlock(lockup, spend);

        assertEquals(TxType.LOCKUP, getTx("lockupTx").getTxType());
        assertCollateralBurnt("spendTx", LOCKUP_AMOUNT, "lockupTx");
    }

    @Test
    public void boundary_aLockupSpendMatchingTheUnlockValuePatternIsAlsoRejected() throws IOException {
        // Before the fork this produced a tx typed LOCKUP whose output 0 was an UNLOCK_OUTPUT, so the collateral did
        // stay locked but the bond the OP_RETURN described did not exist. The rule removes that ambiguity too: the
        // only tx which may spend a lockup output is one which is a plain unlock.
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        parseBlock(rawTx("chainTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT, "chainTx", null),
                        output(1, 0, "chainTx", lockupOpReturn()))));

        assertCollateralBurnt("chainTx", LOCKUP_AMOUNT, "lockupTx");
    }

    @Test
    public void anOrdinaryBsqTransferIsUnaffected() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        long change = GENESIS_SUPPLY - LOCKUP_AMOUNT;

        // Spends only the lockup tx's change output, not the lockup output.
        parseBlock(rawTx("transferTx",
                List.of(new TxInput("lockupTx", 1, null)),
                List.of(output(0, change, "transferTx", null))));

        Tx transferTx = getTx("transferTx");
        assertEquals(TxType.TRANSFER_BSQ, transferTx.getTxType());
        assertEquals(0, transferTx.getBurntBsq());
        assertEquals(TxOutputType.BSQ_OUTPUT, transferTx.getTxOutputs().get(0).getTxOutputType());
        assertTrue(daoStateService.isUnspent(new TxOutputKey("transferTx", 0)));
    }

    @Test
    public void aMixedLockupAndPrematureUnlockSpendAccountsForEveryBurntInput() throws IOException {
        addLockupTx("lockupA", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        addLockupTx("lockupB", "lockupA", 1, GENESIS_SUPPLY - LOCKUP_AMOUNT);
        parseBlock(rawTx("unlockB",
                List.of(new TxInput("lockupB", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT, "unlockB", null))));

        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupA", 0, null), new TxInput("unlockB", 0, null)),
                List.of(output(0, 2 * LOCKUP_AMOUNT, "spendTx", null))));

        assertCollateralBurnt("spendTx", 2 * LOCKUP_AMOUNT, "lockupA", "lockupB");
    }

    @Test
    public void aZeroValuedLockupSpendIsRecordedAsInvalid() throws IOException {
        parseBlock(rawTx("zeroLockup",
                List.of(new TxInput(GENESIS_TX_ID, 0, null)),
                List.of(output(0, 0, "zeroLockup", null),
                        output(1, GENESIS_SUPPLY, "zeroLockup", null),
                        output(2, 0, "zeroLockup", lockupOpReturn()))));
        assertEquals(TxOutputType.LOCKUP_OUTPUT, getTx("zeroLockup").getTxOutputs().get(0).getTxOutputType());

        parseBlock(rawTx("spendZero",
                List.of(new TxInput("zeroLockup", 0, null)),
                List.of(output(0, 0, "spendZero", null),
                        output(1, 0, "spendZero", lockupOpReturn()))));

        Tx spend = getTx("spendZero");
        assertEquals(TxType.INVALID, spend.getTxType());
        assertEquals(0, spend.getBurntBsq());
        assertEquals(TxOutputType.BTC_OUTPUT, spend.getTxOutputs().get(0).getTxOutputType());
        assertEquals("spendZero", daoStateService.getSpentInfo(getTx("zeroLockup").getTxOutputs().get(0))
                .orElseThrow().getTxId());
    }

    @Test
    public void transactionCannotClaimAPreActivationHeightInsideAPostActivationBlock() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        RawTx spend = new RawTx("spendTx",
                ACTIVATION_HEIGHT - 1,
                "blockHash" + nextHeight,
                1000L * nextHeight,
                ImmutableList.of(new TxInput("lockupTx", 0, null)),
                ImmutableList.of(output(0, LOCKUP_AMOUNT, "spendTx", null)));

        assertThrows(InvalidParsingConditionException.class, () -> parseBlock(spend));
        assertTrue(daoStateService.isUnspent(new TxOutputKey("lockupTx", 0)));
    }

    @Test
    public void transactionBlockHashMustMatchItsContainingBlock() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        RawTx spend = new RawTx("spendTx",
                nextHeight,
                "differentBlockHash",
                1000L * nextHeight,
                ImmutableList.of(new TxInput("lockupTx", 0, null)),
                ImmutableList.of(output(0, LOCKUP_AMOUNT, "spendTx", null)));

        assertThrows(InvalidParsingConditionException.class, () -> parseBlock(spend));
        assertTrue(daoStateService.isUnspent(new TxOutputKey("lockupTx", 0)));
    }

    @Test
    public void transactionOutputCannotClaimAPreActivationHeightInsideAPostActivationBlock() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        // The tx level metadata is consistent; only the output claims a pre activation height, which the height
        // gated rules in TxOutputParser would otherwise read.
        RawTx spend = rawTx("spendTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(new RawTxOutput(0, LOCKUP_AMOUNT, "spendTx", null, "address0", null,
                        ACTIVATION_HEIGHT - 1)));

        assertThrows(InvalidParsingConditionException.class, () -> parseBlock(spend));
        assertTrue(daoStateService.isUnspent(new TxOutputKey("lockupTx", 0)));
    }

    @Test
    public void transactionOutputCannotClaimAForeignTxId() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        long change = GENESIS_SUPPLY - LOCKUP_AMOUNT;

        // A plain BSQ transfer of the change output whose output claims the lockup output's key. The UTXO set is
        // addressed by the output's own txId and index, so without validation the resulting BSQ output would
        // replace the lockup output entry and open the bond to an ordinary transfer spend.
        RawTx spend = rawTx("spendTx",
                List.of(new TxInput("lockupTx", 1, null)),
                List.of(new RawTxOutput(0, change, "lockupTx", null, "address0", null, nextHeight)));

        assertThrows(InvalidParsingConditionException.class, () -> parseBlock(spend));
        assertTrue(daoStateService.isUnspent(new TxOutputKey("lockupTx", 0)));
        assertEquals(TxOutputType.LOCKUP_OUTPUT,
                daoStateService.getUnspentTxOutput(new TxOutputKey("lockupTx", 0)).orElseThrow().getTxOutputType());
    }

    private void assertCollateralBurnt(String txId, long expectedBurnt, String... lockupTxIds) {
        Tx tx = getTx(txId);
        assertEquals(TxType.INVALID, tx.getTxType());
        assertEquals(expectedBurnt, tx.getBurntBsq());
        // No value survives as BSQ, so nothing was recovered. The zero value OP_RETURN output keeps its type and is
        // not collateral, so we only look at the outputs which carry value.
        tx.getTxOutputs().stream()
                .filter(txOutput -> txOutput.getValue() > 0)
                .forEach(txOutput -> {
                    assertEquals(TxOutputType.BTC_OUTPUT, txOutput.getTxOutputType(),
                            "output " + txOutput.getIndex() + " survived as BSQ");
                    assertFalse(daoStateService.isUnspent(txOutput.getKey()),
                            "output " + txOutput.getIndex() + " is an unspent BSQ output");
                });
        // A confiscation proposal may still be underway when the burn happens. The collateral is already destroyed,
        // so the confiscation which follows must be a no-op, not an error.
        for (String lockupTxId : lockupTxIds) {
            assertFalse(canConfiscate(lockupTxId), "the burnt bond " + lockupTxId + " was confiscated");
        }
    }
}
