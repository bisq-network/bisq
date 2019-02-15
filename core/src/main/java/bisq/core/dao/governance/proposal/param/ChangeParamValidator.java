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
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.BtcAddressValidator;
import bisq.core.util.validation.InputValidator;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
        double maxDecrease = param.getMaxDecrease();
        double maxIncrease = param.getMaxIncrease();
        Coin newValueAsBtcCoin;
        Coin newValueAsBsqCoin = null;
        Coin currentValueAsCoin;
        switch (param.getParamType()) {
            case UNDEFINED:
                break;
            case BSQ:
                bsqFormatter.validateBsqInput(inputValue);
                newValueAsBsqCoin = bsqFormatter.parseToCoin(inputValue);
                checkArgument(newValueAsBsqCoin.isPositive(), "Input must be positive");
                currentValueAsCoin = daoStateService.getParamValueAsCoin(param, periodService.getChainHeight());
                checkArgument(!currentValueAsCoin.equals(newValueAsBsqCoin), "Your input must be different to the current value");
                validateChangeRange((double) currentValueAsCoin.value, (double) newValueAsBsqCoin.value, maxDecrease, maxIncrease);
                break;
            case BTC:
                bsqFormatter.validateBtcInput(inputValue);
                newValueAsBtcCoin = bsqFormatter.parseToBTC(inputValue);
                checkArgument(!newValueAsBtcCoin.isNegative(), "Input must not be negative");
                // We allow 0 values (arbitration fee)
                checkArgument(newValueAsBtcCoin.value == 0 || newValueAsBtcCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                currentValueAsCoin = daoStateService.getParamValueAsCoin(param, periodService.getChainHeight());
                checkArgument(!currentValueAsCoin.equals(newValueAsBtcCoin), "Your input must be different to the current value");
                validateChangeRange((double) currentValueAsCoin.value, (double) newValueAsBtcCoin.value, maxDecrease, maxIncrease);
                break;
            case PERCENT:
                double newValueAsPercentDouble = bsqFormatter.parsePercentStringToDouble(inputValue);
                checkArgument(newValueAsPercentDouble > 0.5, "Threshold must be larger than 50%.");
                double currentValueAsPercentDouble = daoStateService.getParamValueAsPercentDouble(param, periodService.getChainHeight());
                checkArgument(currentValueAsPercentDouble != newValueAsPercentDouble, "Your input must be different to the current value");
                validateChangeRange(currentValueAsPercentDouble, newValueAsPercentDouble, maxDecrease, maxIncrease);
                break;
            case BLOCK:
                int newValueAsBlock = Integer.parseInt(inputValue);
                // We allow 0 values (e.g. time lock for trade)
                checkArgument(newValueAsBlock >= 0, "newValueAsBlock must be >= 0");
                int currentValueAsBlock = daoStateService.getParamValueAsBlock(param, periodService.getChainHeight());
                checkArgument(currentValueAsBlock != newValueAsBlock, "Your input must be different to the current value");
                validateChangeRange((double) currentValueAsBlock, (double) newValueAsBlock, maxDecrease, maxIncrease);
                break;
            case ADDRESS:
                String currentValue = daoStateService.getParamValue(param, periodService.getChainHeight());
                checkArgument(!currentValue.equals(inputValue), "Your input must be different to the current value");
                InputValidator.ValidationResult validationResult = new BtcAddressValidator().validate(inputValue);
                if (!validationResult.isValid)
                    throw new AddressFormatException(validationResult.errorMessage);

                break;
        }

        // Add here more fine tuned custom validations...
        switch (param) {
            case UNDEFINED:
                break;

            case DEFAULT_MAKER_FEE_BTC:
            case DEFAULT_TAKER_FEE_BTC:
            case MIN_MAKER_FEE_BTC:
            case MIN_TAKER_FEE_BTC:
                break;

            case DEFAULT_MAKER_FEE_BSQ:
            case DEFAULT_TAKER_FEE_BSQ:
            case MIN_MAKER_FEE_BSQ:
            case MIN_TAKER_FEE_BSQ:
                break;

            case PROPOSAL_FEE:
            case BLIND_VOTE_FEE:
                break;

            case COMPENSATION_REQUEST_MIN_AMOUNT:
            case REIMBURSEMENT_MIN_AMOUNT:
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:
                checkNotNull(newValueAsBsqCoin, "newValueAsBsqCoin must not be null");
                checkArgument(newValueAsBsqCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                break;

            case QUORUM_COMP_REQUEST:
            case QUORUM_REIMBURSEMENT:
            case QUORUM_CHANGE_PARAM:
            case QUORUM_ROLE:
            case QUORUM_CONFISCATION:
            case QUORUM_GENERIC:
            case QUORUM_REMOVE_ASSET:
                break;

            case THRESHOLD_COMP_REQUEST:
            case THRESHOLD_REIMBURSEMENT:
            case THRESHOLD_CHANGE_PARAM:
            case THRESHOLD_ROLE:
            case THRESHOLD_CONFISCATION:
            case THRESHOLD_GENERIC:
            case THRESHOLD_REMOVE_ASSET:
                break;

            case RECIPIENT_BTC_ADDRESS:
                break;

            case ASSET_LISTING_FEE_PER_DAY:
                break;
            case ASSET_MIN_VOLUME:
                break;

            case LOCK_TIME_TRADE_PAYOUT:
                break;
            case ARBITRATOR_FEE:
                break;

            case MAX_TRADE_LIMIT:
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
                break;
        }
    }

    private void validateChangeRange(double currentValue, double newValue, double min, double max) throws ValidationException {
        double change = currentValue != 0 ? newValue / currentValue : 0;
        if (max > 0 && change > max)
            throw new ValidationException("Input is larger than " + max + " times the current value.");

        if (min > 0 && change < (1 / min))
            throw new ValidationException("Input is smaller than " + 1 / min + " times the current value.");
    }
}
