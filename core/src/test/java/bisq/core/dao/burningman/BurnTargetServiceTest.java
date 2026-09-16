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

package bisq.core.dao.burningman;

import bisq.core.dao.CyclesInDaoStateService;
import bisq.core.dao.burningman.model.ReimbursementModel;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.ReimbursementProposal;

import org.bitcoinj.core.Coin;

import javafx.collections.FXCollections;

import java.util.List;
import java.util.Set;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BurnTargetServiceTest {
    private static final String TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final int ISSUANCE_HEIGHT = 100;
    private static final long ISSUANCE_AMOUNT = 10_000;

    @Mock
    private DaoStateService daoStateService;
    @Mock
    private CyclesInDaoStateService cyclesInDaoStateService;
    @Mock
    private ProposalService proposalService;
    @InjectMocks
    private BurnTargetService burnTargetService;

    @Test
    void buildsReimbursementModelsFromValidatedProposals() {
        Issuance issuance = reimbursementIssuance();
        ReimbursementProposal proposal = reimbursementProposal("committed");
        when(daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT)).thenReturn(Set.of(issuance));
        when(proposalService.getValidatedProposals()).thenReturn(List.of(proposal));
        when(daoStateService.getBlockTime(ISSUANCE_HEIGHT)).thenReturn(1_234L);
        when(cyclesInDaoStateService.getCycleIndexAtChainHeight(ISSUANCE_HEIGHT)).thenReturn(2);

        Set<ReimbursementModel> reimbursements = burnTargetService.getReimbursements(ISSUANCE_HEIGHT);

        assertEquals(Set.of(new ReimbursementModel(ISSUANCE_AMOUNT, ISSUANCE_HEIGHT, 1_234L, 2, TX_ID)),
                reimbursements);
    }

    @Test
    void doesNotUseRawReimbursementPayloads() {
        Issuance issuance = reimbursementIssuance();
        ProposalPayload poisonedPayload = new ProposalPayload(reimbursementProposal("attacker"));
        when(daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT)).thenReturn(Set.of(issuance));
        when(proposalService.getValidatedProposals()).thenReturn(List.of());
        Mockito.lenient().when(proposalService.getProposalPayloads())
                .thenReturn(FXCollections.observableArrayList(poisonedPayload));

        Set<ReimbursementModel> reimbursements = burnTargetService.getReimbursements(ISSUANCE_HEIGHT);

        assertTrue(reimbursements.isEmpty());
        verify(proposalService, never()).getProposalPayloads();
    }

    @Test
    void validatesProposalsOnceForMultipleIssuances() {
        String secondTxId = "1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        Issuance firstIssuance = reimbursementIssuance();
        Issuance secondIssuance = new Issuance(secondTxId,
                ISSUANCE_HEIGHT,
                ISSUANCE_AMOUNT,
                null,
                IssuanceType.REIMBURSEMENT);
        when(daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT))
                .thenReturn(Set.of(firstIssuance, secondIssuance));
        when(proposalService.getValidatedProposals()).thenReturn(List.of(reimbursementProposal("first"),
                reimbursementProposal("second", secondTxId)));

        Set<ReimbursementModel> reimbursements = burnTargetService.getReimbursements(ISSUANCE_HEIGHT);

        assertEquals(2, reimbursements.size());
        verify(proposalService, times(1)).getValidatedProposals();
    }

    private static Issuance reimbursementIssuance() {
        return new Issuance(TX_ID,
                ISSUANCE_HEIGHT,
                ISSUANCE_AMOUNT,
                null,
                IssuanceType.REIMBURSEMENT);
    }

    private static ReimbursementProposal reimbursementProposal(String name) {
        return reimbursementProposal(name, TX_ID);
    }

    private static ReimbursementProposal reimbursementProposal(String name, String txId) {
        return (ReimbursementProposal) new ReimbursementProposal(name,
                "link",
                Coin.valueOf(ISSUANCE_AMOUNT),
                "B123",
                null).cloneProposal(txId);
    }
}
