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

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.validation.BtcAddressValidator;
import bisq.core.util.validation.InputValidator;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

//TODO Use translation properties in error messages a they are shown to user.

/**
 * We do not store the values as domain types (Coin, int, String) but all as Strings. So we need to parse it to the
 * expected data type even if we get the data not from user input.
 */
@Slf4j
public class ChangeParamValidator extends ProposalValidator {
    public enum Result {
        OK,
        SAME("New parameter value must be different to current value."),
        NO_CHANGE_POSSIBLE("Parameter cannot be changed."),
        TOO_LOW("New parameter value is too small."),
        TOO_HIGH("New parameter value is too large.");

        @Getter
        private final String errorMsg;

        Result(String errorMsg) {
            this.errorMsg = errorMsg;
        }

        Result() {
            this.errorMsg = "";
        }

        @Override
        public String toString() {
            return "Result{" +
                    "\n     errorMsg='" + errorMsg + '\'' +
                    "\n} " + super.toString();
        }
    }

    @Inject
    public ChangeParamValidator(DaoStateService daoStateService, PeriodService periodService) {
        super(daoStateService, periodService);
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ProposalValidationException {
        try {
            super.validateDataFields(proposal);

            ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
            // When we receive a temp proposal the tx is usually not confirmed so we cannot lookup the block height of
            // the tx. We take the current block height in that case as it would be in the same cycle anyway.
            int blockHeight = daoStateService.getTx(proposal.getTxId())
                    .map(BaseTx::getBlockHeight)
                    .orElseGet(daoStateService::getChainHeight);
            validateParamValue(changeParamProposal.getParam(), changeParamProposal.getParamValue(), blockHeight);
        } catch (Throwable throwable) {
            throw new ProposalValidationException(throwable);
        }
    }

    public void validateParamValue(Param param, String inputValue) throws ParamValidationException {
        int blockHeight = periodService.getChainHeight();
        validateParamValue(param, inputValue, blockHeight);
    }

    public void validateParamValue(Param param, String inputValue, int blockHeight) throws ParamValidationException {
        String currentParamValue = daoStateService.getParamValue(param, blockHeight);
        validateParamValue(param, currentParamValue, inputValue);
    }

    private void validateParamValue(Param param, String currentParamValue, String inputValue) throws ParamValidationException {
        try {
            Coin currentParamValueAsCoin, inputValueAsCoin;
            switch (param.getParamType()) {
                case UNDEFINED:
                    break;
                case BSQ:
                    currentParamValueAsCoin = daoStateService.getParamValueAsCoin(param, currentParamValue);
                    inputValueAsCoin = daoStateService.getParamValueAsCoin(param, inputValue);
                    validateBsqValue(currentParamValueAsCoin, inputValueAsCoin, param);
                    break;
                case BTC:
                    currentParamValueAsCoin = daoStateService.getParamValueAsCoin(param, currentParamValue);
                    inputValueAsCoin = daoStateService.getParamValueAsCoin(param, inputValue);
                    validateBtcValue(currentParamValueAsCoin, inputValueAsCoin, param);
                    break;
                case PERCENT:
                    double currentParamValueAsPercentDouble = daoStateService.getParamValueAsPercentDouble(currentParamValue);
                    double inputValueAsPercentDouble = daoStateService.getParamValueAsPercentDouble(inputValue);
                    validatePercentValue(currentParamValueAsPercentDouble, inputValueAsPercentDouble, param);
                    break;
                case BLOCK:
                    int currentParamValueAsBlock = daoStateService.getParamValueAsBlock(currentParamValue);
                    int inputValueAsBlock = daoStateService.getParamValueAsBlock(inputValue);
                    validateBlockValue(currentParamValueAsBlock, inputValueAsBlock, param);
                    break;
                case ADDRESS:
                    validateAddressValue(currentParamValue, inputValue);
                    break;
                default:
                    log.warn("Param type {} not handled in switch case at validateParamValue", param.getParamType());
            }
        } catch (ParamValidationException e) {
            throw e;
        } catch (Throwable t) {
            throw new ParamValidationException(t);
        }
    }

    private void validateBsqValue(Coin currentParamValueAsCoin, Coin inputValueAsCoin, Param param) throws ParamValidationException {
        checkArgument(inputValueAsCoin.isPositive(), "Input must be positive");
        validationChange((double) currentParamValueAsCoin.value, (double) inputValueAsCoin.value, param);

        switch (param) {
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
                checkArgument(inputValueAsCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:
                checkArgument(inputValueAsCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                checkArgument(inputValueAsCoin.value <= 200000000,
                        "Amounts larger than 200 000 BSQ are not permitted");
                break;
            case QUORUM_COMP_REQUEST:
            case QUORUM_REIMBURSEMENT:
            case QUORUM_CHANGE_PARAM:
            case QUORUM_ROLE:
            case QUORUM_CONFISCATION:
            case QUORUM_GENERIC:
            case QUORUM_REMOVE_ASSET:
                checkArgument(inputValueAsCoin.value >= 100000,
                        "Quorum must be at least 1000 BSQ");
                break;
            case ASSET_LISTING_FEE_PER_DAY:
                break;
        }
    }

    private void validateBtcValue(Coin currentParamValueAsCoin, Coin inputValueAsCoin, Param param) throws ParamValidationException {
        validationChange((double) currentParamValueAsCoin.value, (double) inputValueAsCoin.value, param);

        switch (param) {
            case DEFAULT_MAKER_FEE_BTC:
            case DEFAULT_TAKER_FEE_BTC:
            case MIN_MAKER_FEE_BTC:
            case MIN_TAKER_FEE_BTC:
                checkArgument(inputValueAsCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                break;
            case ASSET_MIN_VOLUME:
            case MAX_TRADE_LIMIT:
                checkArgument(inputValueAsCoin.isPositive(), "Input must be positive");
                break;
        }
    }

    private void validatePercentValue(double currentParamValueAsPercentDouble, double inputValueAsPercentDouble, Param param) throws ParamValidationException {
        validationChange(currentParamValueAsPercentDouble, inputValueAsPercentDouble, param);

        switch (param) {
            case THRESHOLD_COMP_REQUEST:
            case THRESHOLD_REIMBURSEMENT:
            case THRESHOLD_CHANGE_PARAM:
            case THRESHOLD_ROLE:
            case THRESHOLD_CONFISCATION:
            case THRESHOLD_GENERIC:
            case THRESHOLD_REMOVE_ASSET:
                // We show only 2 decimals in the UI for % value
                checkArgument(inputValueAsPercentDouble >= 0.5001,
                        "Threshold must be larger than 50%.");
                checkArgument(inputValueAsPercentDouble <= 1,
                        "Threshold cannot be more than 100%.");
                break;
            case ARBITRATOR_FEE:
                checkArgument(inputValueAsPercentDouble >= 0, "Input must not be negative");
                break;
        }
    }

    private void validateBlockValue(int currentParamValueAsBlock, int inputValueAsBlock, Param param) throws ParamValidationException {
        validationChange((double) currentParamValueAsBlock, (double) inputValueAsBlock, param);
        // We allow 0 values (e.g. time lock for trade)
        checkArgument(inputValueAsBlock >= 0, "inputValueAsBlock must be >= 0");

        boolean isMainnet = BisqEnvironment.getBaseCurrencyNetwork().isMainnet();
        switch (param) {
            case LOCK_TIME_TRADE_PAYOUT:
                break;
            case PHASE_UNDEFINED:
                break;
            case PHASE_PROPOSAL:
            case PHASE_BREAK1:
            case PHASE_BLIND_VOTE:
            case PHASE_BREAK2:
            case PHASE_VOTE_REVEAL:
            case PHASE_BREAK3:
                if (isMainnet)
                    checkArgument(inputValueAsBlock >= 6, "The break must have at least 6 blocks");
                break;
            case PHASE_RESULT:
                if (isMainnet)
                    checkArgument(inputValueAsBlock >= 1, "The break must have at least 1 block");
                break;
        }
    }

    private void validateAddressValue(String currentParamValue, String inputValue) {
        checkArgument(!inputValue.equals(currentParamValue), "Your input must be different to the current value");
        InputValidator.ValidationResult validationResult = new BtcAddressValidator().validate(inputValue);
        if (!validationResult.isValid)
            throw new AddressFormatException(validationResult.errorMessage);
    }

    private static void validationChange(double currentParamValue, double inputValue, Param param) throws ParamValidationException {
        Result result = getChangeValidationResult(currentParamValue,
                inputValue,
                param.getMaxDecrease(),
                param.getMaxIncrease());
        if (result != Result.OK) {
            throw new ParamValidationException(result);
        }
    }

    /**
     *
     * @param currentValue      Current value
     * @param newValue          New value
     * @param min               Decrease of param value limited to current value / maxDecrease. If 0 we don't apply the check and any change is possible
     * @param max               Increase of param value limited to current value * maxIncrease. If 0 we don't apply the check and any change is possible
     * @return Validation result
     */
    @VisibleForTesting
    static Result getChangeValidationResult(double currentValue, double newValue, double min, double max) {
        checkArgument(min >= 0, "Min must be >= 0");
        checkArgument(max >= 0, "Max must be >= 0");
        if (currentValue == newValue)
            return Result.SAME;

        if (max == 0)
            return Result.OK;

        double change = currentValue != 0 ? newValue / currentValue : 0;
        if (change > max)
            return Result.TOO_HIGH;

        if (min == 0)
            return Result.OK;

        // If min/max are > 0 and currentValue or newValue is 0 it cannot be changed. min/max must be 0 in such cases.
        if (currentValue == 0 || newValue == 0)
            return Result.NO_CHANGE_POSSIBLE;

        if (change < (1 / min))
            return Result.TOO_LOW;

        return Result.OK;
    }
}
