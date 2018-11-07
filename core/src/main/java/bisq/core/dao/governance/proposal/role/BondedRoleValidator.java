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

import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.period.PeriodService;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.Validate.notEmpty;

@Slf4j
public class BondedRoleValidator extends ProposalValidator {

    @Inject
    public BondedRoleValidator(DaoStateService daoStateService, PeriodService periodService) {
        super(daoStateService, periodService);
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ValidationException {
        try {
            super.validateDataFields(proposal);

            BondedRoleProposal bondedRoleProposal = (BondedRoleProposal) proposal;
            BondedRole bondedRole = bondedRoleProposal.getBondedRole();

            //TODO
            notEmpty(bondedRole.getName(), "bondedRole.name must not be empty");

        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }
}
