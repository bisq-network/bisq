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

package bisq.core.dao.governance.blindvote;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.DaoPhase;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlindVoteValidatorTest {
    private static final String TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final int TX_HEIGHT = 100;
    private static final int CHAIN_HEIGHT = 110;

    @Test
    void areDataFieldsValidAndTxConfirmedRequiresOpReturnCommitmentToMatchEncryptedVotes() throws Exception {
        BlindVote committedBlindVote = blindVote(new byte[]{0x01, 0x02, 0x03});
        BlindVote forgedBlindVote = blindVote(new byte[]{0x09, 0x02, 0x03});
        BlindVoteValidator blindVoteValidator = new BlindVoteValidator(mockDaoStateService(committedBlindVote,
                TxType.BLIND_VOTE),
                mock(PeriodService.class));

        assertTrue(blindVoteValidator.areDataFieldsValidAndTxConfirmed(committedBlindVote));
        assertFalse(blindVoteValidator.areDataFieldsValidAndTxConfirmed(forgedBlindVote));
    }

    @Test
    void isTxInPhaseAndCycleRequiresOpReturnCommitmentToMatchEncryptedVotes() throws Exception {
        BlindVote committedBlindVote = blindVote(new byte[]{0x01, 0x02, 0x03});
        BlindVote forgedBlindVote = blindVote(new byte[]{0x09, 0x02, 0x03});
        PeriodService periodService = mockPeriodService();
        BlindVoteValidator blindVoteValidator = new BlindVoteValidator(mockDaoStateService(committedBlindVote,
                TxType.BLIND_VOTE),
                periodService);

        assertTrue(blindVoteValidator.isTxInPhaseAndCycle(committedBlindVote));
        assertFalse(blindVoteValidator.isTxInPhaseAndCycle(forgedBlindVote));
    }

    @Test
    void isTxInPhaseAndCycleRejectsNonBlindVoteTxType() throws Exception {
        BlindVote blindVote = blindVote(new byte[]{0x01, 0x02, 0x03});
        BlindVoteValidator blindVoteValidator = new BlindVoteValidator(mockDaoStateService(blindVote, TxType.PROPOSAL),
                mockPeriodService());

        assertFalse(blindVoteValidator.isTxInPhaseAndCycle(blindVote));
    }

    @Test
    void areDataFieldsValidAndTxConfirmedRejectsNonBlindVoteTxType() throws Exception {
        BlindVote blindVote = blindVote(new byte[]{0x01, 0x02, 0x03});
        BlindVoteValidator blindVoteValidator = new BlindVoteValidator(mockDaoStateService(blindVote, TxType.PROPOSAL),
                mock(PeriodService.class));

        assertFalse(blindVoteValidator.areDataFieldsValidAndTxConfirmed(blindVote));
    }

    private DaoStateService mockDaoStateService(BlindVote committedBlindVote, TxType txType) throws Exception {
        TxOutput txOutput = mock(TxOutput.class);
        when(txOutput.getOpReturnData()).thenReturn(getOpReturnData(committedBlindVote));

        Tx tx = mock(Tx.class);
        when(tx.getTxType()).thenReturn(txType);
        when(tx.getBlockHeight()).thenReturn(TX_HEIGHT);
        when(tx.getLastTxOutput()).thenReturn(txOutput);

        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getChainHeight()).thenReturn(CHAIN_HEIGHT);
        when(daoStateService.getTx(TX_ID)).thenReturn(Optional.of(tx));
        when(daoStateService.isParseBlockChainComplete()).thenReturn(true);
        return daoStateService;
    }

    private PeriodService mockPeriodService() {
        PeriodService periodService = mock(PeriodService.class);
        when(periodService.isTxInCorrectCycle(TX_HEIGHT, CHAIN_HEIGHT)).thenReturn(true);
        when(periodService.isTxInPhase(TX_ID, DaoPhase.Phase.BLIND_VOTE)).thenReturn(true);
        return periodService;
    }

    private BlindVote blindVote(byte[] encryptedVotes) {
        return new BlindVote(encryptedVotes,
                TX_ID,
                Restrictions.getMinNonDustOutput().value,
                new byte[]{0x04, 0x05},
                1_700_000_000_000L);
    }

    private byte[] getOpReturnData(BlindVote blindVote) throws Exception {
        byte[] hash = BlindVoteConsensus.getHashOfEncryptedVotes(blindVote.getEncryptedVotes());
        return BlindVoteConsensus.getOpReturnData(hash);
    }
}
