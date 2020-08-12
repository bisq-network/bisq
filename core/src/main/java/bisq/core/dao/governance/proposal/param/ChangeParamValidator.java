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
import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.validation.BtcAddressValidator;
import bisq.core.util.validation.InputValidator;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Changes here can potentially break consensus!
 *
 * We do not store the values as the actual data type (Coin, int, String) but as Strings. So we need to convert it to the
 * expected data type even if we get the data not from user input.
 */
@Slf4j
public class ChangeParamValidator extends ProposalValidator implements ConsensusCritical {

    private final BsqFormatter bsqFormatter;

    @Inject
    public ChangeParamValidator(DaoStateService daoStateService, PeriodService periodService, BsqFormatter bsqFormatter) {
        super(daoStateService, periodService);
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ProposalValidationException {
        try {
            super.validateDataFields(proposal);

            // Only once parsing is complete we can check for param changes
            if (daoStateService.isParseBlockChainComplete()) {
                ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
                validateParamValue(changeParamProposal.getParam(), changeParamProposal.getParamValue(), getBlockHeight(proposal));
                checkArgument(changeParamProposal.getParamValue().length() <= 200, "ParamValue must not exceed 200 chars");
            }
        } catch (ProposalValidationException e) {
            throw e;
        } catch (Throwable throwable) {
            throw new ProposalValidationException(throwable);
        }
    }

    public void validateParamValue(Param param, String inputValue) throws ParamValidationException {
        int blockHeight = periodService.getChainHeight();
        validateParamValue(param, inputValue, blockHeight);
    }

    private void validateParamValue(Param param, String inputValue, int blockHeight) throws ParamValidationException {
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
        } catch (NumberFormatException t) {
            throw new ParamValidationException(Res.get("validation.numberFormatException", t.getMessage().toLowerCase()));
        } catch (Throwable t) {
            throw new ParamValidationException(t);
        }
    }

    private void validateBsqValue(Coin currentParamValueAsCoin, Coin inputValueAsCoin, Param param) throws ParamValidationException {
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
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:
                checkArgument(inputValueAsCoin.value >= Restrictions.getMinNonDustOutput().value,
                        Res.get("validation.amountBelowDust", Restrictions.getMinNonDustOutput().value));
                checkArgument(inputValueAsCoin.value <= 200000000,
                        Res.get("validation.inputTooLarge", "200 000 BSQ"));
                break;
            case QUORUM_COMP_REQUEST:
            case QUORUM_REIMBURSEMENT:
            case QUORUM_CHANGE_PARAM:
            case QUORUM_ROLE:
            case QUORUM_CONFISCATION:
            case QUORUM_GENERIC:
            case QUORUM_REMOVE_ASSET:
                checkArgument(inputValueAsCoin.value > 100000,
                        Res.get("validation.inputTooSmall", "1000 BSQ"));
                break;
            case ASSET_LISTING_FEE_PER_DAY:
                break;
            case BONDED_ROLE_FACTOR:
                checkArgument(inputValueAsCoin.value > 100,
                        Res.get("validation.inputTooSmall", "1 BSQ"));
                break;
        }
        checkArgument(inputValueAsCoin.isPositive(), Res.get("validation.inputTooSmall", "0 BSQ"));
        validationChange((double) currentParamValueAsCoin.value, (double) inputValueAsCoin.value, param);
    }

    private void validateBtcValue(Coin currentParamValueAsCoin, Coin inputValueAsCoin, Param param) throws ParamValidationException {
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
                checkArgument(inputValueAsCoin.isPositive(), Res.get("validation.inputTooSmall", "0"));
                break;
        }
        validationChange((double) currentParamValueAsCoin.value, (double) inputValueAsCoin.value, param);
    }

    private void validatePercentValue(double currentParamValueAsPercentDouble, double inputValueAsPercentDouble, Param param) throws ParamValidationException {
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
                        Res.get("validation.inputTooSmall", "50%"));
                checkArgument(inputValueAsPercentDouble <= 1,
                        Res.get("validation.inputTooLarge", "100%"));
                break;
            case ARBITRATOR_FEE:
                checkArgument(inputValueAsPercentDouble >= 0, Res.get("validation.mustNotBeNegative"));
                break;
        }
        validationChange(currentParamValueAsPercentDouble, inputValueAsPercentDouble, param);
    }

    private void validateBlockValue(int currentParamValueAsBlock, int inputValueAsBlock, Param param) throws ParamValidationException {
        boolean isMainnet = Config.baseCurrencyNetwork().isMainnet();
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
                    checkArgument(inputValueAsBlock >= 6, Res.get("validation.inputToBeAtLeast", "6 blocks"));
                break;
            case PHASE_RESULT:
                if (isMainnet)
                    checkArgument(inputValueAsBlock >= 1, Res.get("validation.inputToBeAtLeast", "1 block"));
                break;
        }

        validationChange((double) currentParamValueAsBlock, (double) inputValueAsBlock, param);
        // We allow 0 values (e.g. time lock for trade)
        checkArgument(inputValueAsBlock >= 0, Res.get("validation.mustNotBeNegative"));

    }

    private void validateAddressValue(String currentParamValue, String inputValue) throws ParamValidationException {
        checkArgument(!inputValue.equals(currentParamValue), Res.get("validation.mustBeDifferent"));
        InputValidator.ValidationResult validationResult = new BtcAddressValidator().validate(inputValue);
        if (!validationResult.isValid)
            throw new ParamValidationException(validationResult.errorMessage);
    }

    private void validationChange(double currentParamValue, double inputValue, Param param) throws ParamValidationException {
        validationChange(currentParamValue,
                inputValue,
                param.getMaxDecrease(),
                param.getMaxIncrease(),
                param);
    }

    /**
     *  @param currentValue      Current value
     * @param newValue          New value
     * @param min               Decrease of param value limited to current value / maxDecrease. If 0 we don't apply the check and any change is possible
     * @param max               Increase of param value limited to current value * maxIncrease. If 0 we don't apply the check and any change is possible
     * @param param
     */
    @VisibleForTesting
    void validationChange(double currentValue, double newValue, double min, double max, Param param) throws ParamValidationException {
        // No need for translation as it would be a developer error to use such min/max values
        checkArgument(min >= 0, "Min must be >= 0");
        checkArgument(max >= 0, "Max must be >= 0");
        if (currentValue == newValue) {
            throw new ParamValidationException(ParamValidationException.ERROR.SAME, Res.get("validation.mustBeDifferent"));
        }

        if (max == 0)
            return;

        //TODO some cases with min = 0 and max not 0 or the other way round are not correctly implemented yet.
        // Not intended to be used that way anyway but should be fixed...
        double change = currentValue != 0 ? newValue / currentValue : 0;
        if (change > max) {
            double val = currentValue * max;
            String value = getFormattedValue(param, val);
            throw new ParamValidationException(ParamValidationException.ERROR.TOO_HIGH, Res.get("validation.inputTooLarge", value));
        }

        if (min == 0)
            return;

        // If min/max are > 0 and currentValue is 0 it cannot be changed. min/max must be 0 in such cases.
        if (currentValue == 0) {
            throw new ParamValidationException(ParamValidationException.ERROR.NO_CHANGE_POSSIBLE, Res.get("validation.cannotBeChanged"));
        }

        if (change < (1 / min)) {
            double val = currentValue / min;
            String value = getFormattedValue(param, val);
            throw new ParamValidationException(ParamValidationException.ERROR.TOO_LOW, Res.get("validation.inputToBeAtLeast", value));
        }
    }

    private String getFormattedValue(Param param, double val) {
        String value = String.valueOf(val);
        switch (param.getParamType()) {
            case UNDEFINED:
                // Not used
                break;
            case BSQ:
                value = bsqFormatter.formatBSQSatoshis((long) val);
                break;
            case BTC:
                value = bsqFormatter.formatBTCSatoshis((long) val);
                break;
            case PERCENT:
                value = String.valueOf(val * 100);
                break;
            case BLOCK:
                value = String.valueOf(Math.round(val));
                break;
            case ADDRESS:
                // Not used here
                break;
        }
        return bsqFormatter.formatParamValue(param, value);

    }
}
