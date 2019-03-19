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

import bisq.core.dao.governance.proposal.compensation.CompensationValidator;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondValidator;
import bisq.core.dao.governance.proposal.generic.GenericProposalValidator;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementValidator;
import bisq.core.dao.governance.proposal.removeAsset.RemoveAssetValidator;
import bisq.core.dao.governance.proposal.role.RoleValidator;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.GenericProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.RoleProposal;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProposalValidatorProvider {
    private final CompensationValidator compensationValidator;
    private final ConfiscateBondValidator confiscateBondValidator;
    private final GenericProposalValidator genericProposalValidator;
    private final ChangeParamValidator changeParamValidator;
    private final ReimbursementValidator reimbursementValidator;
    private final RemoveAssetValidator removeAssetValidator;
    private final RoleValidator roleValidator;

    @Inject
    public ProposalValidatorProvider(CompensationValidator compensationValidator,
                                     ConfiscateBondValidator confiscateBondValidator,
                                     GenericProposalValidator genericProposalValidator,
                                     ChangeParamValidator changeParamValidator,
                                     ReimbursementValidator reimbursementValidator,
                                     RemoveAssetValidator removeAssetValidator,
                                     RoleValidator roleValidator) {
        this.compensationValidator = compensationValidator;
        this.confiscateBondValidator = confiscateBondValidator;
        this.genericProposalValidator = genericProposalValidator;
        this.changeParamValidator = changeParamValidator;
        this.reimbursementValidator = reimbursementValidator;
        this.removeAssetValidator = removeAssetValidator;
        this.roleValidator = roleValidator;
    }

    public ProposalValidator getValidator(Proposal proposal) {
        if (proposal instanceof CompensationProposal)
            return compensationValidator;
        else if (proposal instanceof ConfiscateBondProposal)
            return confiscateBondValidator;
        else if (proposal instanceof GenericProposal)
            return genericProposalValidator;
        else if (proposal instanceof ChangeParamProposal)
            return changeParamValidator;
        else if (proposal instanceof ReimbursementProposal)
            return reimbursementValidator;
        else if (proposal instanceof RemoveAssetProposal)
            return removeAssetValidator;
        else if (proposal instanceof RoleProposal)
            return roleValidator;
        else
            throw new RuntimeException("");
    }
}
