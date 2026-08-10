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

package bisq.core.dao.governance.bond.reputation;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;

import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BondedReputationRepositoryTest {
    private static final int MIN_EXPORTED_LOCK_TIME = 50_000;
    private static final byte[] BONDED_ROLE_HASH = new byte[20];
    private static final byte[] REPUTATION_HASH = new byte[]{
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20
    };

    @Test
    public void onlyReputationLockupsBecomeBondedReputation() throws IOException {
        DaoStateService daoStateService = mock(DaoStateService.class);
        Set<TxOutput> lockupTxOutputs = new HashSet<>();
        Map<String, Tx> txById = new HashMap<>();
        Map<String, TxOutput> opReturnOutputByTxId = new HashMap<>();

        when(daoStateService.getLockupTxOutputs()).thenReturn(lockupTxOutputs);
        when(daoStateService.getTx(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(txById.get(invocation.<String>getArgument(0))));
        when(daoStateService.getLockupOpReturnTxOutput(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        opReturnOutputByTxId.get(invocation.<String>getArgument(0))));
        when(daoStateService.isUnspent(any())).thenReturn(true);

        addLockup("bondedRoleTx", LockupReason.BONDED_ROLE, BONDED_ROLE_HASH, MIN_EXPORTED_LOCK_TIME,
                lockupTxOutputs, txById, opReturnOutputByTxId);
        addLockup("reputationTx", LockupReason.REPUTATION, REPUTATION_HASH, MIN_EXPORTED_LOCK_TIME,
                lockupTxOutputs, txById, opReturnOutputByTxId);

        BondedReputationRepository repository = new BondedReputationRepository(
                daoStateService,
                mock(BsqWalletService.class));

        repository.update();

        assertEquals(1, repository.getBonds().size());
        BondedReputation bondedReputation = repository.getBonds().getFirst();
        assertEquals("reputationTx", bondedReputation.getLockupTxId());
        assertEquals(MIN_EXPORTED_LOCK_TIME, bondedReputation.getLockTime());
        assertArrayEquals(REPUTATION_HASH, bondedReputation.getBondedAsset().getHash());
    }

    private static void addLockup(String txId,
                                  LockupReason lockupReason,
                                  byte[] hash,
                                  int lockTime,
                                  Set<TxOutput> lockupTxOutputs,
                                  Map<String, Tx> txById,
                                  Map<String, TxOutput> opReturnOutputByTxId) throws IOException {
        TxOutput lockupOutput = mock(TxOutput.class);
        when(lockupOutput.getTxId()).thenReturn(txId);
        when(lockupOutput.getKey()).thenReturn(new TxOutputKey(txId, 0));
        lockupTxOutputs.add(lockupOutput);

        TxOutput opReturnOutput = mock(TxOutput.class);
        when(opReturnOutput.getOpReturnData())
                .thenReturn(BondConsensus.getLockupOpReturnData(lockTime, lockupReason, hash));
        opReturnOutputByTxId.put(txId, opReturnOutput);

        Tx lockupTx = mock(Tx.class);
        when(lockupTx.getId()).thenReturn(txId);
        when(lockupTx.getLockTime()).thenReturn(lockTime);
        when(lockupTx.getLastTxOutput()).thenReturn(opReturnOutput);
        txById.put(txId, lockupTx);
    }
}
