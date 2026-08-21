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

package bisq.core.dao.governance.proposal;

import bisq.core.dao.DaoHardFork;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.compensation.CompensationValidator;
import bisq.core.dao.governance.proposal.generic.GenericProposalValidator;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.util.coin.BsqFormatter;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProposalValidatorTest {
    private static final String TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final byte NON_CURRENT_VERSION = (byte) 0x02;
    private static final int TX_HEIGHT = 100;
    private static final int CHAIN_HEIGHT = 110;

    @Test
    public void isTxTypeValidRequiresOpReturnCommitmentToMatchProposalPayload() {
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(TX_ID);
        Proposal tamperedProposal = new GenericProposal("tampered name", "https://bisq.network", null)
                .cloneProposal(TX_ID);
        ProposalValidator proposalValidator = new GenericProposalValidator(mockDaoStateService(proposal),
                mock(PeriodService.class));

        assertTrue(proposalValidator.isTxTypeValid(proposal));
        assertFalse(proposalValidator.isTxTypeValid(tamperedProposal));
    }

    @Test
    public void isValidAndConfirmedRequiresOpReturnCommitmentToMatchProposalPayload() {
        Proposal proposal = new GenericProposal("name", "https://bisq.network", null)
                .cloneProposal(TX_ID);
        Proposal tamperedProposal = new GenericProposal("tampered name", "https://bisq.network", null)
                .cloneProposal(TX_ID);
        PeriodService periodService = mockPeriodService();
        ProposalValidator proposalValidator = new GenericProposalValidator(mockDaoStateService(proposal),
                periodService);

        assertTrue(proposalValidator.isValidAndConfirmed(proposal));
        assertFalse(proposalValidator.isValidAndConfirmed(tamperedProposal));
    }

    @Test
    public void isTxTypeValidUsesProposalVersionForOpReturnCommitment() {
        Proposal proposal = withVersion(new GenericProposal("name", "https://bisq.network", null)
                        .cloneProposal(TX_ID),
                NON_CURRENT_VERSION);
        ProposalValidator proposalValidator = new GenericProposalValidator(mockDaoStateService(proposal),
                mock(PeriodService.class));

        assertTrue(proposalValidator.isTxTypeValid(proposal));
    }

    @Test
    public void isTxTypeValidUsesCompensationRequestOpReturnType() {
        Proposal proposal = new CompensationProposal("name",
                "https://bisq.network",
                Coin.valueOf(10000),
                "B123",
                null)
                .cloneProposal(TX_ID);
        ProposalValidator proposalValidator = new CompensationValidator(mockDaoStateService(proposal),
                mock(PeriodService.class));

        assertTrue(proposalValidator.isTxTypeValid(proposal));
    }

    @Test
    public void isTxTypeValidUsesReimbursementRequestOpReturnType() {
        Proposal proposal = new ReimbursementProposal("name",
                "https://bisq.network",
                Coin.valueOf(10000),
                "B123",
                null)
                .cloneProposal(TX_ID);
        ProposalValidator proposalValidator = new ReimbursementValidator(mockDaoStateService(proposal),
                mock(PeriodService.class));

        assertTrue(proposalValidator.isTxTypeValid(proposal));
    }

    @Test
    public void consensusDataFieldValidationActivatesForTheWholeProposalCycle() {
        Proposal proposal = new GenericProposal("x".repeat(201), "https://bisq.network", null)
                .cloneProposal(TX_ID);
        DaoStateService daoStateService = mockDaoStateService(proposal);
        PeriodService periodService = mock(PeriodService.class);
        ProposalValidator proposalValidator = new GenericProposalValidator(daoStateService, periodService);
        int activationHeight = DaoHardFork.getProposalDataFieldValidationActivationHeight();

        when(periodService.getFirstBlockOfPhase(TX_HEIGHT, DaoPhase.Phase.RESULT))
                .thenReturn(activationHeight - 1, activationHeight);

        assertTrue(proposalValidator.isValidForConsensus(proposal));
        assertFalse(proposalValidator.isValidForConsensus(proposal));
    }

    @Test
    public void activatedConsensusValidationDoesNotSkipChangeParamFieldsDuringInitialParsing() {
        Proposal proposal = new ChangeParamProposal("name",
                "https://bisq.network",
                Param.UNDEFINED,
                "x".repeat(201),
                null)
                .cloneProposal(TX_ID);
        DaoStateService daoStateService = mockDaoStateService(proposal);
        PeriodService periodService = mock(PeriodService.class);
        ProposalValidator proposalValidator = new ChangeParamValidator(daoStateService,
                periodService,
                new BsqFormatter());
        int activationHeight = DaoHardFork.getProposalDataFieldValidationActivationHeight();

        when(daoStateService.isParseBlockChainComplete()).thenReturn(false);
        when(daoStateService.getParamValue(Param.UNDEFINED, TX_HEIGHT)).thenReturn("0");
        when(periodService.getFirstBlockOfPhase(TX_HEIGHT, DaoPhase.Phase.RESULT)).thenReturn(activationHeight);

        assertTrue(proposalValidator.areDataFieldsValid(proposal));
        assertFalse(proposalValidator.isValidForConsensus(proposal));
    }

    private DaoStateService mockDaoStateService(Proposal proposal) {
        TxOutput txOutput = mock(TxOutput.class);
        when(txOutput.getOpReturnData()).thenReturn(getOpReturnData(proposal));

        Tx tx = mock(Tx.class);
        when(tx.getTxType()).thenReturn(proposal.getTxType());
        when(tx.getBlockHeight()).thenReturn(TX_HEIGHT);
        when(tx.getLastTxOutput()).thenReturn(txOutput);

        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getChainHeight()).thenReturn(CHAIN_HEIGHT);
        when(daoStateService.getTx(TX_ID)).thenReturn(Optional.of(tx));
        return daoStateService;
    }

    private PeriodService mockPeriodService() {
        PeriodService periodService = mock(PeriodService.class);
        when(periodService.isTxInCorrectCycle(TX_HEIGHT, CHAIN_HEIGHT)).thenReturn(true);
        when(periodService.isInPhase(TX_HEIGHT, DaoPhase.Phase.PROPOSAL)).thenReturn(true);
        return periodService;
    }

    private byte[] getOpReturnData(Proposal proposal) {
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal.cloneProposal(null));
        return ProposalConsensus.getOpReturnData(hashOfPayload, getOpReturnType(proposal).getType(), proposal.getVersion());
    }

    private OpReturnType getOpReturnType(Proposal proposal) {
        if (proposal instanceof CompensationProposal) {
            return OpReturnType.COMPENSATION_REQUEST;
        } else if (proposal instanceof ReimbursementProposal) {
            return OpReturnType.REIMBURSEMENT_REQUEST;
        } else {
            return OpReturnType.PROPOSAL;
        }
    }

    private Proposal withVersion(Proposal proposal, byte version) {
        return Proposal.fromProto(proposal.getProposalBuilder()
                .setVersion(version)
                .build());
    }
}
