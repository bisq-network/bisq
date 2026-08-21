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

import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;


import java.io.IOException;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the DAO parser does when a LOCKUP output is spent by a transaction which is not a formal UNLOCK, below the
 * hard fork 3 activation height. This is the behaviour the fork changes; see {@link LockupOutputSpendHardForkTest}
 * for the rules from the activation height on.
 * <p>
 * Heights are above the hard fork 1 activation height (605000 on mainnet) so all other current rules apply.
 */
public class LockupOutputSpendTest extends LockupOutputSpendTestBase {
    // Well below the hard fork 3 activation height.
    private static final int GENESIS_HEIGHT = 700_000;

    @BeforeEach
    public void setup() {
        startChainAt(GENESIS_HEIGHT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Control: the honest path behaves as designed
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void aLockupTxCreatesALockedOutput() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        Tx lockupTx = getTx("lockupTx");
        assertEquals(TxType.LOCKUP, lockupTx.getTxType());
        assertEquals(TxOutputType.LOCKUP_OUTPUT, lockupTx.getTxOutputs().get(0).getTxOutputType());
        assertEquals(LOCK_TIME, lockupTx.getTxOutputs().get(0).getLockTime());
        assertEquals(LOCKUP_AMOUNT, lockupTx.getLockedAmount());
        assertTrue(daoStateService.isUnspent(new TxOutputKey("lockupTx", 0)));
        // The DAO can confiscate it while it is unspent.
        assertTrue(canConfiscate("lockupTx"));
    }

    @Test
    public void aCanonicalUnlockTxIsRecognisedAndTheLockTimeApplies() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        // Canonical unlock: exactly one lockup input, one output at index 0 of exactly the same value.
        parseBlock(rawTx("unlockTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT, "unlockTx", null))));

        Tx unlockTx = getTx("unlockTx");
        assertEquals(TxType.UNLOCK, unlockTx.getTxType());
        TxOutput unlockOutput = unlockTx.getTxOutputs().get(0);
        assertEquals(TxOutputType.UNLOCK_OUTPUT, unlockOutput.getTxOutputType());
        // The lock time is what protects the DAO: the output is not spendable until it has passed.
        int unlockTxHeight = nextHeight - 1;
        assertEquals(unlockTxHeight + LOCK_TIME, unlockOutput.getUnlockBlockHeight());
        assertFalse(daoStateService.isTxOutputSpendable(unlockOutput.getKey()));
        // And confiscation still reaches it while it is unlocking.
        assertTrue(canConfiscate("lockupTx"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // The claim: a lockup output can be spent without an unlock tx, and the BSQ survives
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void shapeA_splittingTheLockupValueIntoTwoOutputsRecoversTheBsq() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        // Not the unlock shape: output 0 does not equal the whole available input value.
        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT / 2, "spendTx", null),
                        output(1, LOCKUP_AMOUNT / 2, "spendTx", null))));

        assertCollateralRecovered("spendTx", "lockupTx", 2, LOCKUP_AMOUNT);
    }

    @Test
    public void shapeB_anExtraBsqInputBreaksTheExactValueMatch() throws IOException {
        // A second BSQ utxo to spend alongside the lockup output.
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        long change = GENESIS_SUPPLY - LOCKUP_AMOUNT;

        // Single output, but availableInputValue (lockup + change) != output value, so it is not an unlock.
        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupTx", 0, null), new TxInput("lockupTx", 1, null)),
                List.of(output(0, LOCKUP_AMOUNT + change, "spendTx", null))));

        assertCollateralRecovered("spendTx", "lockupTx", 1, LOCKUP_AMOUNT + change);
    }

    @Test
    public void shapeC_aNewLockupTxCanRollAnOldBondForwardWithoutUnlocking() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        // The OP_RETURN decides the tx type, so the unlock branch is never reached. The new lockup value differs
        // from the old one, so the unlock value pattern does not match either (see the boundary test below).
        parseBlock(rawTx("chainTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, 300_000, "chainTx", null),
                        output(1, 200_000, "chainTx", null),
                        output(2, 0, "chainTx", lockupOpReturn()))));

        Tx chainTx = getTx("chainTx");
        assertEquals(TxType.LOCKUP, chainTx.getTxType());
        assertEquals(0, chainTx.getBurntBsq());
        // A brand new bond, with a fresh lock time. Nothing carried over from the old one.
        assertEquals(TxOutputType.LOCKUP_OUTPUT, chainTx.getTxOutputs().get(0).getTxOutputType());
        assertEquals(LOCK_TIME, chainTx.getTxOutputs().get(0).getLockTime());
        // The remainder is plain spendable BSQ.
        assertEquals(TxOutputType.BSQ_OUTPUT, chainTx.getTxOutputs().get(1).getTxOutputType());
        assertTrue(daoStateService.isTxOutputSpendable(new TxOutputKey("chainTx", 1)));

        // The old lockup output is spent by a tx which is not an unlock, so the old bond ends with no unlock tx,
        // and a confiscation proposal naming the old lockup tx id can no longer reach anything.
        assertFalse(daoStateService.isUnspent(new TxOutputKey("lockupTx", 0)));
        assertEquals("chainTx", daoStateService.getSpentInfo(getTx("lockupTx").getTxOutputs().get(0))
                .orElseThrow().getTxId());
        assertFalse(canConfiscate("lockupTx"));
    }

    @Test
    public void boundary_aLockupSpendMatchingTheUnlockValuePatternIsTreatedAsAnUnlock() throws IOException {
        // The parser decides "is this an unlock" purely from the value shape, before it looks at the OP_RETURN.
        // If the new lockup happens to have exactly the value of the old one, output 0 becomes an UNLOCK_OUTPUT
        // even though the tx is typed LOCKUP. The collateral then does stay locked and confiscatable, but the new
        // bond the OP_RETURN describes does not exist, as there is no LOCKUP_OUTPUT.
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);

        parseBlock(rawTx("chainTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT, "chainTx", null),
                        output(1, 0, "chainTx", lockupOpReturn()))));

        Tx chainTx = getTx("chainTx");
        assertEquals(TxType.LOCKUP, chainTx.getTxType());
        assertEquals(TxOutputType.UNLOCK_OUTPUT, chainTx.getTxOutputs().get(0).getTxOutputType());
        assertTrue(canConfiscate("lockupTx"));
    }

    @Test
    public void shapeD_twoLockupOutputsCanBeSpentInOneTx() throws IOException {
        addLockupTx("lockupA", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        // Re-lock the change into a second bond so we have two lockup outputs to spend at once.
        addLockupTx("lockupB", "lockupA", 1, GENESIS_SUPPLY - LOCKUP_AMOUNT);

        long total = LOCKUP_AMOUNT + LOCKUP_AMOUNT;
        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupA", 0, null), new TxInput("lockupB", 0, null)),
                List.of(output(0, total / 2, "spendTx", null),
                        output(1, total / 2, "spendTx", null))));

        Tx spendTx = getTx("spendTx");
        // isUnLockInputValid is false here, but that is only consulted inside the UNLOCK branch of evaluateTxType,
        // which this tx never reaches, so nothing rejects it.
        assertEquals(TxType.TRANSFER_BSQ, spendTx.getTxType());
        assertEquals(0, spendTx.getBurntBsq());
        assertFalse(canConfiscate("lockupA"));
        assertFalse(canConfiscate("lockupB"));
        assertEquals(total, spendTx.getTxOutputs().stream()
                .filter(o -> o.getTxOutputType() == TxOutputType.BSQ_OUTPUT)
                .filter(o -> daoStateService.isUnspent(o.getKey()))
                .mapToLong(TxOutput::getValue)
                .sum());
    }

    @Test
    public void theRecoveredBsqIsImmediatelySpendableAgain() throws IOException {
        addLockupTx("lockupTx", GENESIS_TX_ID, 0, GENESIS_SUPPLY);
        parseBlock(rawTx("spendTx",
                List.of(new TxInput("lockupTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT / 2, "spendTx", null),
                        output(1, LOCKUP_AMOUNT / 2, "spendTx", null))));

        // No lock time was applied anywhere, so the value can move on in the very next block.
        assertTrue(daoStateService.isTxOutputSpendable(new TxOutputKey("spendTx", 0)));
        parseBlock(rawTx("onwardTx",
                List.of(new TxInput("spendTx", 0, null)),
                List.of(output(0, LOCKUP_AMOUNT / 2, "onwardTx", null))));

        Tx onwardTx = getTx("onwardTx");
        assertEquals(TxType.TRANSFER_BSQ, onwardTx.getTxType());
        assertEquals(TxOutputType.BSQ_OUTPUT, onwardTx.getTxOutputs().get(0).getTxOutputType());
        assertTrue(daoStateService.isUnspent(new TxOutputKey("onwardTx", 0)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void assertCollateralRecovered(String spendTxId,
                                           String lockupTxId,
                                           int expectedBsqOutputs,
                                           long expectedRecoveredValue) {
        Tx spendTx = getTx(spendTxId);
        assertNotEquals(TxType.INVALID, spendTx.getTxType(), "the spend was not rejected by the parser");
        assertEquals(TxType.TRANSFER_BSQ, spendTx.getTxType());
        assertEquals(0, spendTx.getBurntBsq(), "no BSQ was burnt");

        List<TxOutput> bsqOutputs = spendTx.getTxOutputs().stream()
                .filter(o -> o.getTxOutputType() == TxOutputType.BSQ_OUTPUT)
                .toList();
        assertEquals(expectedBsqOutputs, bsqOutputs.size());
        bsqOutputs.forEach(o -> assertTrue(daoStateService.isUnspent(o.getKey())));
        // No output carries an unlock block height, so no lock time constrains the recovered value.
        bsqOutputs.forEach(o -> assertTrue(daoStateService.isTxOutputSpendable(o.getKey())));
        assertEquals(expectedRecoveredValue, bsqOutputs.stream().mapToLong(TxOutput::getValue).sum());

        assertFalse(daoStateService.isUnspent(new TxOutputKey(lockupTxId, 0)));
        // And the DAO can no longer confiscate the bond.
        assertFalse(canConfiscate(lockupTxId), "the bond was still confiscatable");
    }
}
