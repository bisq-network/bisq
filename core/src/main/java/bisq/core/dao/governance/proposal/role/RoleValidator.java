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
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.Validate.notEmpty;

@Slf4j
public class RoleValidator extends ProposalValidator {

    @Inject
    public RoleValidator(DaoStateService daoStateService, PeriodService periodService) {
        super(daoStateService, periodService);
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ValidationException {
        try {
            super.validateDataFields(proposal);

            RoleProposal roleProposal = (RoleProposal) proposal;
            Role role = roleProposal.getRole();

            //TODO
            notEmpty(role.getName(), "role.name must not be empty");

        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }
}
