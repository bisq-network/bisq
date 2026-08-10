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

package bisq.core.dao.governance.proposal.role;

import bisq.core.dao.DaoHardFork;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalConsensus;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoleValidatorTest {
    private static final String TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final BondedRoleType ROLE_TYPE = BondedRoleType.BTC_NODE_OPERATOR;

    @Test
    public void matchingBondTermsAreValidFromActivationHeight() {
        RoleProposal proposal = createProposal();
        RoleValidator validator = createValidator(proposal, DaoHardFork.getHardFork3ActivationHeight());

        assertTrue(validator.areDataFieldsValid(proposal));
        assertTrue(validator.isValidForConsensus(proposal));
    }

    @Test
    public void mismatchedRequiredBondUnitIsInvalidFromActivationHeight() {
        RoleProposal proposal = withBondTerms(createProposal(),
                ROLE_TYPE.getRequiredBondUnit() + 1,
                ROLE_TYPE.getUnlockTimeInBlocks());
        RoleValidator validator = createValidator(proposal, DaoHardFork.getHardFork3ActivationHeight());

        assertFalse(validator.areDataFieldsValid(proposal));
        assertFalse(validator.isValidForConsensus(proposal));
    }

    @Test
    public void mismatchedUnlockTimeIsInvalidFromActivationHeight() {
        RoleProposal proposal = withBondTerms(createProposal(),
                ROLE_TYPE.getRequiredBondUnit(),
                ROLE_TYPE.getUnlockTimeInBlocks() - 1);
        RoleValidator validator = createValidator(proposal, DaoHardFork.getHardFork3ActivationHeight());

        assertFalse(validator.areDataFieldsValid(proposal));
        assertFalse(validator.isValidForConsensus(proposal));
    }

    @Test
    public void historicalMismatchedBondTermsRemainValidBeforeActivationHeight() {
        RoleProposal proposal = withBondTerms(createProposal(),
                ROLE_TYPE.getRequiredBondUnit() + 1,
                ROLE_TYPE.getUnlockTimeInBlocks() - 1);
        RoleValidator validator = createValidator(proposal, DaoHardFork.getHardFork3ActivationHeight() - 1);

        assertTrue(validator.areDataFieldsValid(proposal));
        assertTrue(validator.isValidForConsensus(proposal));
    }

    private RoleProposal createProposal() {
        Role role = new Role("name", "https://bisq.network", ROLE_TYPE);
        return (RoleProposal) new RoleProposal(role, new TreeMap<>()).cloneProposal(TX_ID);
    }

    private RoleProposal withBondTerms(RoleProposal proposal, long requiredBondUnit, int unlockTime) {
        protobuf.Proposal.Builder builder = proposal.getProposalBuilder();
        builder.getRoleProposalBuilder()
                .setRequiredBondUnit(requiredBondUnit)
                .setUnlockTime(unlockTime);
        return (RoleProposal) Proposal.fromProto(builder.build());
    }

    private RoleValidator createValidator(RoleProposal proposal, int blockHeight) {
        TxOutput txOutput = mock(TxOutput.class);
        when(txOutput.getOpReturnData()).thenReturn(getOpReturnData(proposal));

        Tx tx = mock(Tx.class);
        when(tx.getTxType()).thenReturn(proposal.getTxType());
        when(tx.getBlockHeight()).thenReturn(blockHeight);
        when(tx.getLastTxOutput()).thenReturn(txOutput);

        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getChainHeight()).thenReturn(blockHeight);
        when(daoStateService.getTx(TX_ID)).thenReturn(Optional.of(tx));
        return new RoleValidator(daoStateService, mock(PeriodService.class));
    }

    private byte[] getOpReturnData(RoleProposal proposal) {
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal.cloneProposal(null));
        return ProposalConsensus.getOpReturnData(hashOfPayload,
                OpReturnType.PROPOSAL.getType(),
                proposal.getVersion());
    }
}
