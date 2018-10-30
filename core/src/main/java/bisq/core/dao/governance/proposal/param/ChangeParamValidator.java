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
import bisq.core.util.validation.BtcAddressValidator;
import bisq.core.util.validation.InputValidator;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

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

    public void validateParamValue(Param param, String inputValue) throws ValidationException {
        double min = 2;
        double max = 2;
        Coin newValueAsCoin = null;
        Coin currentValueAsCoin;

        switch (param) {
            case UNDEFINED:
                break;

            case DEFAULT_MAKER_FEE_BTC:
            case DEFAULT_TAKER_FEE_BTC:
            case MIN_MAKER_FEE_BTC:
            case MIN_TAKER_FEE_BTC:
                bsqFormatter.validateBtcInput(inputValue);
                newValueAsCoin = bsqFormatter.parseToBTC(inputValue);
                min = 5;
                max = 5;
                checkArgument(newValueAsCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                break;

            case DEFAULT_MAKER_FEE_BSQ:
            case DEFAULT_TAKER_FEE_BSQ:
            case MIN_MAKER_FEE_BSQ:
            case MIN_TAKER_FEE_BSQ:

            case PROPOSAL_FEE:
            case BLIND_VOTE_FEE:
                bsqFormatter.validateBsqInput(inputValue);
                newValueAsCoin = bsqFormatter.parseToCoin(inputValue);
                min = 5;
                max = 5;
                break;

            case COMPENSATION_REQUEST_MIN_AMOUNT:
            case REIMBURSEMENT_MIN_AMOUNT:
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:
                bsqFormatter.validateBsqInput(inputValue);
                newValueAsCoin = bsqFormatter.parseToCoin(inputValue);
                min = 4;
                max = 2;
                checkArgument(newValueAsCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                break;

            case QUORUM_COMP_REQUEST:
            case QUORUM_REIMBURSEMENT:
            case QUORUM_CHANGE_PARAM:
            case QUORUM_ROLE:
            case QUORUM_CONFISCATION:
            case QUORUM_GENERIC:
            case QUORUM_REMOVE_ASSET:
                bsqFormatter.validateBsqInput(inputValue);
                newValueAsCoin = bsqFormatter.parseToCoin(inputValue);
                min = 2;
                max = 2;
                break;

            case THRESHOLD_COMP_REQUEST:
            case THRESHOLD_REIMBURSEMENT:
            case THRESHOLD_CHANGE_PARAM:
            case THRESHOLD_ROLE:
            case THRESHOLD_CONFISCATION:
            case THRESHOLD_GENERIC:
            case THRESHOLD_REMOVE_ASSET:
                double newValueAsPercentDouble = bsqFormatter.parsePercentStringToDouble(inputValue);
                checkArgument(newValueAsPercentDouble > 0.5, "Threshold must be larger than 50%.");
                double currentValueAsPercentDouble = daoStateService.getParamValueAsPercentDouble(param, periodService.getChainHeight());
                min = 1.2;
                max = 1.2;
                validateChangeRange(currentValueAsPercentDouble, newValueAsPercentDouble, min, max);
                break;

            case RECIPIENT_BTC_ADDRESS:
                InputValidator.ValidationResult validationResult = new BtcAddressValidator().validate(inputValue);
                if (!validationResult.isValid)
                    throw new AddressFormatException(validationResult.errorMessage);
                break;

            case PHASE_UNDEFINED:
                break;
            case PHASE_PROPOSAL:
            case PHASE_BREAK1:
            case PHASE_BLIND_VOTE:
            case PHASE_BREAK2:
            case PHASE_VOTE_REVEAL:
            case PHASE_BREAK3:
            case PHASE_RESULT:
                int newValueAsBlock = Integer.parseInt(inputValue);
                checkArgument(newValueAsBlock > 0, "newValueAsBlock must be > 0");
                int currentValueAsBlock = daoStateService.getParamValueAsBlock(param, periodService.getChainHeight());
                min = 2;
                max = 2;
                validateChangeRange((double) currentValueAsBlock, (double) newValueAsBlock, min, max);
                break;
        }

        if (newValueAsCoin != null) {
            checkArgument(newValueAsCoin.isPositive(), "Input must be positive");
            currentValueAsCoin = daoStateService.getParamValueAsCoin(param, periodService.getChainHeight());
            validateChangeRange((double) currentValueAsCoin.value, (double) newValueAsCoin.value, min, max);
        }
    }

    private void validateChangeRange(double currentValue, double newValue, double min, double max) throws ValidationException {
        double change = newValue / currentValue;
        if (change > max)
            throw new ValidationException("Input is larger than " + max + " times the current value.");

        if (change < (1 / min))
            throw new ValidationException("Input is smaller than " + 1 / min + " % of the current value.");
    }
}
