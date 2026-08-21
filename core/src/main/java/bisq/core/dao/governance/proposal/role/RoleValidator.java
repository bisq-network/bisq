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

package bisq.core.dao.governance.proposal.role;

import bisq.core.dao.DaoHardFork;
import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.Validate.notNull;

/**
 * Changes here can potentially break consensus!
 */
@Slf4j
public class RoleValidator extends ProposalValidator implements ConsensusCritical {

    @Inject
    public RoleValidator(DaoStateService daoStateService, PeriodService periodService) {
        super(daoStateService, periodService);
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ProposalValidationException {
        try {
            super.validateDataFields(proposal);

            RoleProposal roleProposal = (RoleProposal) proposal;
            notNull(roleProposal.getRole(), "Bonded role must not be null");
            validateBondTerms(roleProposal);
        } catch (Throwable throwable) {
            throw new ProposalValidationException(throwable);
        }
    }

    @Override
    protected void validateConsensusDataFields(Proposal proposal) throws ProposalValidationException {
        try {
            validateBondTerms((RoleProposal) proposal);
        } catch (Throwable throwable) {
            throw new ProposalValidationException(throwable);
        }
    }

    private void validateBondTerms(RoleProposal roleProposal) {
        if (!DaoHardFork.isHardFork3Activated(getBlockHeight(roleProposal))) {
            return;
        }

        Role role = notNull(roleProposal.getRole(), "Bonded role must not be null");
        BondedRoleType bondedRoleType = notNull(role.getBondedRoleType(), "Bonded role type must not be null");
        checkArgument(roleProposal.getRequiredBondUnit() == bondedRoleType.getRequiredBondUnit(),
                "Required bond unit must match the bonded role type");
        checkArgument(roleProposal.getUnlockTime() == bondedRoleType.getUnlockTimeInBlocks(),
                "Unlock time must match the bonded role type");
    }
}
