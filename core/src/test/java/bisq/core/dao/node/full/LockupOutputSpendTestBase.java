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

import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.node.parser.TxParser;
import bisq.core.dao.node.parser.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.node.parser.exceptions.BlockHeightNotConnectingException;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.util.coin.BsqFormatter;

import com.google.common.collect.ImmutableList;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Harness for the lockup spend tests. Shared by {@link LockupOutputSpendTest} and
 * {@link LockupOutputSpendHardForkTest}, which cover the rules before and from the hard fork 3 activation height.
 * <p>
 * These tests drive real {@link RawBlock}s through the production {@link BlockParser}, {@link TxParser} and
 * {@link DaoStateService} against a real {@link DaoState}. Nothing in the path under test is mocked; only
 * {@link PeriodService} is a stub, and it is never consulted for the transaction types used here (it is only used
 * for proposal, issuance and blind vote fee/phase checks).
 * <p>
 * Heights are above the hard fork 1 activation height (605000 on mainnet) so the current rules apply.
 */
abstract class LockupOutputSpendTestBase {
    static final String GENESIS_TX_ID = "genesisTx";
    static final long GENESIS_SUPPLY = 1_000_000L;
    static final long LOCKUP_AMOUNT = 500_000L;
    static final int LOCK_TIME = 15_840; // the mainnet bonded role lock time, 110 days

    DaoStateService daoStateService;
    private BlockParser blockParser;
    private String previousBlockHash;
    int nextHeight;
    private String pendingConfiscationLockupTxId;

    void startChainAt(int genesisHeight) {
        DaoState daoState = new DaoState();
        GenesisTxInfo genesisTxInfo = new GenesisTxInfo(GENESIS_TX_ID, genesisHeight, GENESIS_SUPPLY);
        daoStateService = new DaoStateService(daoState, genesisTxInfo, new BsqFormatter());
        blockParser = new BlockParser(new TxParser(mock(PeriodService.class), daoStateService), daoStateService);

        // VoteResultService confiscates from within onParseBlockComplete, which is the only phase in which the dao
        // state may still be changed. We mirror that so the confiscation attempts below run exactly as in production.
        daoStateService.addDaoStateListener(new DaoStateListener() {
            @Override
            public void onParseBlockComplete(Block block) {
                if (pendingConfiscationLockupTxId != null) {
                    String lockupTxId = pendingConfiscationLockupTxId;
                    pendingConfiscationLockupTxId = null;
                    daoStateService.confiscateBond(lockupTxId);
                }
            }
        });

        nextHeight = genesisHeight;
        previousBlockHash = "blockHash" + (genesisHeight - 1);
        // Genesis block: one output carrying the whole supply, which every later tx spends from.
        parseBlock(rawTx(GENESIS_TX_ID, List.of(), List.of(output(0, GENESIS_SUPPLY, GENESIS_TX_ID, null))));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Runs the production confiscation path in the next block, as an accepted confiscation proposal would, and
     * reports whether the bond was actually confiscated.
     */
    boolean canConfiscate(String lockupTxId) {
        pendingConfiscationLockupTxId = lockupTxId;
        parseBlock();
        return daoStateService.isConfiscatedLockupTxOutput(lockupTxId);
    }

    void addLockupTx(String txId, String inputTxId, int inputIndex, long inputValue) throws IOException {
        List<RawTxOutput> outputs = new ArrayList<>();
        outputs.add(output(0, LOCKUP_AMOUNT, txId, null));
        long change = inputValue - LOCKUP_AMOUNT;
        if (change > 0) {
            outputs.add(output(1, change, txId, null));
        }
        outputs.add(output(outputs.size(), 0, txId, lockupOpReturn()));
        parseBlock(rawTx(txId, List.of(new TxInput(inputTxId, inputIndex, null)), outputs));
        assertEquals(TxType.LOCKUP, getTx(txId).getTxType());
    }

    static byte[] lockupOpReturn() throws IOException {
        return BondConsensus.getLockupOpReturnData(LOCK_TIME, LockupReason.BONDED_ROLE, new byte[20]);
    }

    Tx getTx(String txId) {
        return daoStateService.getTx(txId).orElseThrow(() -> new AssertionError("No tx " + txId));
    }

    RawTx rawTx(String txId, List<TxInput> inputs, List<RawTxOutput> outputs) {
        return new RawTx(txId, nextHeight, blockHash(nextHeight), 1000L * nextHeight,
                ImmutableList.copyOf(inputs), ImmutableList.copyOf(outputs));
    }

    RawTxOutput output(int index, long value, String txId, byte[] opReturnData) {
        return new RawTxOutput(index, value, txId, null, opReturnData == null ? "address" + index : null,
                opReturnData, nextHeight);
    }

    void parseBlock(RawTx... txs) {
        RawBlock rawBlock = new RawBlock(nextHeight, 1000L * nextHeight, blockHash(nextHeight), previousBlockHash,
                ImmutableList.copyOf(Arrays.asList(txs)));
        try {
            blockParser.parseBlock(rawBlock);
        } catch (BlockHashNotConnectingException | BlockHeightNotConnectingException e) {
            throw new AssertionError(e);
        }
        previousBlockHash = blockHash(nextHeight);
        nextHeight++;
    }

    private static String blockHash(int height) {
        return "blockHash" + height;
    }
}
