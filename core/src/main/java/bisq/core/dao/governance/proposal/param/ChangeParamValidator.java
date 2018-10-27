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

package bisq.core.dao.governance.proposal.param;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.governance.Param;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class ChangeParamValidator extends ProposalValidator {

    private BsqFormatter bsqFormatter;

    @Inject
    public ChangeParamValidator(DaoStateService daoStateService, PeriodService periodService, BsqFormatter bsqFormatter) {
        super(daoStateService, periodService);
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ValidationException {
        try {
            super.validateDataFields(proposal);

            ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;

            validateParamValue(changeParamProposal.getParam(), changeParamProposal.getParamValue());
        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }

    // TODO: Get the last checks in place.
    public void validateParamValue(Param param, long paramValue) throws ChangeParamValidationException {

        // max 4 times the current value. min 25% of current value as general boundaries
        checkMinMaxForProposedValue(param, paramValue, 4, 4);

        switch (param) {
            case UNDEFINED:
                break;
            case DEFAULT_MAKER_FEE_BSQ:
                // max twice the current value and min half of current value as suggested boundaries
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;
            case DEFAULT_TAKER_FEE_BSQ:
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;
            case MIN_MAKER_FEE_BSQ:
                break;
            case MIN_TAKER_FEE_BSQ:
                break;
            case DEFAULT_MAKER_FEE_BTC:
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;
            case DEFAULT_TAKER_FEE_BTC:
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;
            case MIN_MAKER_FEE_BTC:
                break;
            case MIN_TAKER_FEE_BTC:
                break;

            case PROPOSAL_FEE:
                checkMinMaxForProposedValue(param, paramValue, 4, 4);
                break;
            case BLIND_VOTE_FEE:
                checkMinMaxForProposedValue(param, paramValue, 4, 4);
                break;

            case COMPENSATION_REQUEST_MIN_AMOUNT:
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;
            case COMPENSATION_REQUEST_MAX_AMOUNT:
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;
            case REIMBURSEMENT_MIN_AMOUNT:
                if (paramValue < Restrictions.getMinNonDustOutput().value) {
                        throw new ChangeParamValidationException(Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                }
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;
            case REIMBURSEMENT_MAX_AMOUNT:
                checkMinMaxForProposedValue(param, paramValue, 2, 2);
                break;

            case QUORUM_COMP_REQUEST:
                break;
            case QUORUM_REIMBURSEMENT:
                break;
            case QUORUM_CHANGE_PARAM:
                break;
            case QUORUM_ROLE:
                break;
            case QUORUM_CONFISCATION:
                break;
            case QUORUM_GENERIC:
                break;
            case QUORUM_REMOVE_ASSET:
                break;

            case THRESHOLD_COMP_REQUEST:
                break;
            case THRESHOLD_REIMBURSEMENT:
                break;
            case THRESHOLD_CHANGE_PARAM:
                break;
            case THRESHOLD_ROLE:
                break;
            case THRESHOLD_CONFISCATION:
                break;
            case THRESHOLD_GENERIC:
                break;
            case THRESHOLD_REMOVE_ASSET:
                break;

            case PHASE_UNDEFINED:
                break;
            case PHASE_PROPOSAL:
                break;
            case PHASE_BREAK1:
                break;
            case PHASE_BLIND_VOTE:
                break;
            case PHASE_BREAK2:
                break;
            case PHASE_VOTE_REVEAL:
                break;
            case PHASE_BREAK3:
                break;
            case PHASE_RESULT:
                break;
        }
    }

    @VisibleForTesting
    void checkMinMaxForProposedValue(Param param, long proposedNewValue, long maxFactorChange, long minFactorChange) throws ChangeParamValidationException {
        long currentValue = getCurrentValue(param);
        validateMinValue(param, currentValue, proposedNewValue, minFactorChange);
        validateMaxValue(param, currentValue, proposedNewValue, maxFactorChange);
    }

    @VisibleForTesting
    void validateMinValue(Param param, long currentValue, long proposedNewValue, long factor) throws ChangeParamValidationException {
        if ((proposedNewValue * factor) < currentValue){
            throw new ChangeParamValidationException(Res.get("validation.inputTooSmall",
                    bsqFormatter.formatParamValue(param,10000/factor))
            );
        }
    }

    @VisibleForTesting
    void validateMaxValue(Param param, long currentValue, long proposedNewValue, long factor) throws ChangeParamValidationException {
        if (proposedNewValue > (currentValue * factor)){
            throw new ChangeParamValidationException(Res.get("validation.inputTooLarge",
                    bsqFormatter.formatParamValue(param, (10000 * factor)))
            );
        }
    }

    private long getCurrentValue(Param param) {
        return daoStateService.getParamValue(param, periodService.getChainHeight());
    }
}
