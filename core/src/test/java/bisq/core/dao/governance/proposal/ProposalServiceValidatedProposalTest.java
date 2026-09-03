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

package bisq.core.dao.governance.proposal;

import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.compensation.CompensationValidator;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStorageService;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStorageService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProposalServiceValidatedProposalTest {
    private static final String TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void excludesCompensationBodyNotCommittedByItsTransaction() {
        CompensationProposal committedProposal = compensationProposal("committed");
        CompensationProposal forgedProposal = compensationProposal("attacker");
        DaoStateService daoStateService = mockDaoStateService(committedProposal);
        PeriodService periodService = mock(PeriodService.class);
        CompensationValidator validator = new CompensationValidator(daoStateService, periodService);
        ProposalValidatorProvider validatorProvider = mock(ProposalValidatorProvider.class);
        when(validatorProvider.getValidator(any(Proposal.class))).thenReturn(validator);
        ProposalService proposalService = newProposalService(daoStateService, periodService, validatorProvider);
        proposalService.getProposalPayloads().addAll(new ProposalPayload(committedProposal),
                new ProposalPayload(forgedProposal));

        List<Proposal> validatedProposals = proposalService.getValidatedProposals();

        assertEquals(List.of(committedProposal), validatedProposals);
    }

    private static ProposalService newProposalService(DaoStateService daoStateService,
                                                       PeriodService periodService,
                                                       ProposalValidatorProvider validatorProvider) {
        return new ProposalService(mock(P2PService.class),
                periodService,
                mock(ProposalStorageService.class),
                mock(TempProposalStorageService.class),
                mock(AppendOnlyDataStoreService.class),
                mock(ProtectedDataStoreService.class),
                daoStateService,
                validatorProvider);
    }

    private static DaoStateService mockDaoStateService(CompensationProposal committedProposal) {
        TxOutput txOutput = mock(TxOutput.class);
        when(txOutput.getOpReturnData()).thenReturn(getOpReturnData(committedProposal));
        Tx tx = mock(Tx.class);
        when(tx.getTxType()).thenReturn(TxType.COMPENSATION_REQUEST);
        when(tx.getLastTxOutput()).thenReturn(txOutput);
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getTx(TX_ID)).thenReturn(Optional.of(tx));
        return daoStateService;
    }

    private static CompensationProposal compensationProposal(String name) {
        return (CompensationProposal) new CompensationProposal(name,
                "link",
                Coin.valueOf(10_000),
                "B123",
                null).cloneProposal(TX_ID);
    }

    private static byte[] getOpReturnData(Proposal proposal) {
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal.cloneProposal(null));
        return ProposalConsensus.getOpReturnData(hashOfPayload,
                OpReturnType.COMPENSATION_REQUEST.getType(),
                proposal.getVersion());
    }
}
