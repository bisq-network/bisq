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

    // TODO
    public void validateParamValue(Param param, long paramValue) throws ChangeParamValidationException {
        // max 4 times the current value. min 25% of current value as general boundaries
        checkMinMax(param, paramValue, 300, -75);

        switch (param) {
            case UNDEFINED:
                break;

            case DEFAULT_MAKER_FEE_BSQ:
                break;
            case DEFAULT_TAKER_FEE_BSQ:
                break;
            case MIN_MAKER_FEE_BSQ:
                break;
            case MIN_TAKER_FEE_BSQ:
                break;
            case DEFAULT_MAKER_FEE_BTC:
                break;
            case DEFAULT_TAKER_FEE_BTC:
                break;
            case MIN_MAKER_FEE_BTC:
                break;
            case MIN_TAKER_FEE_BTC:
                break;

            case PROPOSAL_FEE:
                break;
            case BLIND_VOTE_FEE:
                break;

            case COMPENSATION_REQUEST_MIN_AMOUNT:
            case REIMBURSEMENT_MIN_AMOUNT:
                if (paramValue < Restrictions.getMinNonDustOutput().value)
                    throw new ChangeParamValidationException(Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                checkMinMax(param, paramValue, 100, -50);
                break;
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:
                checkMinMax(param, paramValue, 100, -50);
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

    private void checkMinMax(Param param, long paramValue, long maxPercentChange, long minPercentChange) throws ChangeParamValidationException {
        long max = getNewValueByPercentChange(param, maxPercentChange);
        if (paramValue > max)
            throw new ChangeParamValidationException(Res.get("validation.inputTooLarge", bsqFormatter.formatParamValue(param, max)));
        long min = getNewValueByPercentChange(param, minPercentChange);
        if (paramValue < min)
            throw new ChangeParamValidationException(Res.get("validation.inputTooSmall", bsqFormatter.formatParamValue(param, min)));
    }

    /**
     * @param param         The param to change
     * @param percentChange 100 means 100% more than current value -> 2 times current value. -50 means half of the current value
     * @return The new value.
     */
    //TODO add test
    // TODO use multiplier to make it more intuitive? (4,4) means 4 times current value for max and divided by 4 to get min value)
    @VisibleForTesting
    long getNewValueByPercentChange(Param param, long percentChange) {
        checkArgument(percentChange > -100, "percentChange must be bigger than -100");
        return (getCurrentValue(param) * 100 * (100 + percentChange)) / 10000;
    }

    private long getCurrentValue(Param param) {
        return daoStateService.getParamValue(param, periodService.getChainHeight());
    }
}
