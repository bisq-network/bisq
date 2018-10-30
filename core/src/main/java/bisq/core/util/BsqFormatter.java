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

package bisq.core.util;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.state.governance.Param;
import bisq.core.locale.Res;
import bisq.core.provider.price.MarketPrice;
import bisq.core.util.validation.BtcAddressValidator;
import bisq.core.util.validation.InputValidator;

import bisq.common.app.DevEnv;
import bisq.common.util.MathUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import javax.inject.Inject;

import java.text.DecimalFormat;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BsqFormatter extends BSFormatter {
    @SuppressWarnings("PointlessBooleanExpression")
    private static final boolean useBsqAddressFormat = true || !DevEnv.isDevMode();
    private final String prefix = "B";
    private final DecimalFormat amountFormat = new DecimalFormat("###,###,###.##");
    private final DecimalFormat marketCapFormat = new DecimalFormat("###,###,###");
    private final MonetaryFormat btcCoinFormat;

    @Inject
    public BsqFormatter() {
        super();

        btcCoinFormat = super.coinFormat;

        final String baseCurrencyCode = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode();
        switch (baseCurrencyCode) {
            case "BTC":
                coinFormat = new MonetaryFormat().shift(6).code(6, "BSQ").minDecimals(2);
                break;
            case "LTC":
                coinFormat = new MonetaryFormat().shift(3).code(3, "BSQ").minDecimals(5);
                break;
            case "DASH":
                // BSQ for DASH not used/supported
                coinFormat = new MonetaryFormat().shift(3).code(3, "???").minDecimals(5);
                break;
            default:
                throw new RuntimeException("baseCurrencyCode not defined. baseCurrencyCode=" + baseCurrencyCode);
        }

        amountFormat.setMinimumFractionDigits(2);
    }

    /**
     * Returns the base-58 encoded String representation of this
     * object, including version and checksum bytes.
     */
    public String getBsqAddressStringFromAddress(Address address) {
        final String addressString = address.toString();
        if (useBsqAddressFormat)
            return prefix + addressString;
        else
            return addressString;

    }

    public Address getAddressFromBsqAddress(String encoded) {
        if (useBsqAddressFormat)
            encoded = encoded.substring(prefix.length(), encoded.length());

        try {
            return Address.fromBase58(BisqEnvironment.getParameters(), encoded);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }
    }

    public String formatAmountWithGroupSeparatorAndCode(Coin amount) {
        return amountFormat.format(MathUtils.scaleDownByPowerOf10(amount.value, 2)) + " BSQ";
    }

    public String formatMarketCap(MarketPrice bsqPriceMarketPrice, MarketPrice fiatMarketPrice, Coin issuedAmount) {
        if (bsqPriceMarketPrice != null && fiatMarketPrice != null) {
            double marketCap = bsqPriceMarketPrice.getPrice() * fiatMarketPrice.getPrice() * (MathUtils.scaleDownByPowerOf10(issuedAmount.value, 2));
            return marketCapFormat.format(MathUtils.doubleToLong(marketCap)) + " " + fiatMarketPrice.getCurrencyCode();
        } else {
            return "";
        }
    }

    public String formatBTCWithCode(long satoshi) {
        return super.formatCoinWithCode(satoshi, btcCoinFormat);
    }

    public String formatBTCWithCode(Coin coin) {
        return super.formatCoinWithCode(coin, btcCoinFormat);
    }

    public String formatBTC(Coin coin) {
        return super.formatCoin(coin.value, btcCoinFormat);
    }

    public Coin parseToBTC(String input) {
        return super.parseToCoin(input, btcCoinFormat);
    }

    public void validateBtcInput(String input) throws ValidationException {
        validateCoinInput(input, btcCoinFormat);
    }

    public void validateBsqInput(String input) throws ValidationException {
        validateCoinInput(input, this.coinFormat);
    }

    private void validateCoinInput(String input, MonetaryFormat coinFormat) throws ValidationException {
        try {
            coinFormat.parse(cleanDoubleInput(input));
        } catch (Throwable t) {
            throw new ValidationException("Invalid format for a " + coinFormat.code() + " value");
        }
    }

    public String formatParamValue(Param param, String value) {
        switch (param) {
            case UNDEFINED:
                throw new IllegalArgumentException("Unsupported param: " + param);

            case DEFAULT_MAKER_FEE_BTC:
            case DEFAULT_TAKER_FEE_BTC:
            case MIN_MAKER_FEE_BTC:
            case MIN_TAKER_FEE_BTC:
                return formatBTCWithCode(parseToBTC(value));

            case DEFAULT_MAKER_FEE_BSQ:
            case DEFAULT_TAKER_FEE_BSQ:
            case MIN_MAKER_FEE_BSQ:
            case MIN_TAKER_FEE_BSQ:

            case PROPOSAL_FEE:
            case BLIND_VOTE_FEE:
            case COMPENSATION_REQUEST_MIN_AMOUNT:
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MIN_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:

            case QUORUM_COMP_REQUEST:
            case QUORUM_REIMBURSEMENT:
            case QUORUM_CHANGE_PARAM:
            case QUORUM_ROLE:
            case QUORUM_CONFISCATION:
            case QUORUM_GENERIC:
            case QUORUM_REMOVE_ASSET:
                return formatCoinWithCode(parseToCoin(value));

            case THRESHOLD_COMP_REQUEST:
            case THRESHOLD_REIMBURSEMENT:
            case THRESHOLD_CHANGE_PARAM:
            case THRESHOLD_ROLE:
            case THRESHOLD_CONFISCATION:
            case THRESHOLD_GENERIC:
            case THRESHOLD_REMOVE_ASSET:
                return formatToPercentWithSymbol(parsePercentStringToDouble(value));

            case RECIPIENT_BTC_ADDRESS:
                return value;

            case PHASE_UNDEFINED:
                throw new IllegalArgumentException("Unsupported param: " + param);
            case PHASE_PROPOSAL:
            case PHASE_BREAK1:
            case PHASE_BLIND_VOTE:
            case PHASE_BREAK2:
            case PHASE_VOTE_REVEAL:
            case PHASE_BREAK3:
            case PHASE_RESULT:
                return Res.get("dao.param.blocks", Integer.parseInt(value));
            default:
                throw new IllegalArgumentException("Unsupported param: " + param);
        }
    }

    public Coin parseParamValueToCoin(Param param, String inputValue) {
        switch (param) {
            case DEFAULT_MAKER_FEE_BTC:
            case DEFAULT_TAKER_FEE_BTC:
            case MIN_MAKER_FEE_BTC:
            case MIN_TAKER_FEE_BTC:
                return parseToBTC(inputValue);

            case DEFAULT_MAKER_FEE_BSQ:
            case DEFAULT_TAKER_FEE_BSQ:
            case MIN_MAKER_FEE_BSQ:
            case MIN_TAKER_FEE_BSQ:

            case PROPOSAL_FEE:
            case BLIND_VOTE_FEE:
            case COMPENSATION_REQUEST_MIN_AMOUNT:
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MIN_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:

            case QUORUM_COMP_REQUEST:
            case QUORUM_REIMBURSEMENT:
            case QUORUM_CHANGE_PARAM:
            case QUORUM_ROLE:
            case QUORUM_CONFISCATION:
            case QUORUM_GENERIC:
            case QUORUM_REMOVE_ASSET:
                return parseToCoin(inputValue);

            default:
                throw new IllegalArgumentException("Unsupported param: " + param);
        }
    }

    public int parseParamValueToBlocks(Param param, String inputValue) {
        switch (param) {
            case PHASE_UNDEFINED:
                return 0;
            case PHASE_PROPOSAL:
            case PHASE_BREAK1:
            case PHASE_BLIND_VOTE:
            case PHASE_BREAK2:
            case PHASE_VOTE_REVEAL:
            case PHASE_BREAK3:
            case PHASE_RESULT:
                return Integer.parseInt(inputValue);
            default:
                throw new IllegalArgumentException("Unsupported param: " + param);
        }
    }

    public String parseParamValueToString(Param param, String inputValue) throws ValidationException {
        switch (param) {
            case UNDEFINED:
                throw new IllegalArgumentException("Unsupported param: " + param);

            case DEFAULT_MAKER_FEE_BTC:
            case DEFAULT_TAKER_FEE_BTC:
            case MIN_MAKER_FEE_BTC:
            case MIN_TAKER_FEE_BTC:
                return formatBTC(parseParamValueToCoin(param, inputValue));

            case DEFAULT_MAKER_FEE_BSQ:
            case DEFAULT_TAKER_FEE_BSQ:
            case MIN_MAKER_FEE_BSQ:
            case MIN_TAKER_FEE_BSQ:

            case PROPOSAL_FEE:
            case BLIND_VOTE_FEE:
            case COMPENSATION_REQUEST_MIN_AMOUNT:
            case COMPENSATION_REQUEST_MAX_AMOUNT:
            case REIMBURSEMENT_MIN_AMOUNT:
            case REIMBURSEMENT_MAX_AMOUNT:

            case QUORUM_COMP_REQUEST:
            case QUORUM_REIMBURSEMENT:
            case QUORUM_CHANGE_PARAM:
            case QUORUM_ROLE:
            case QUORUM_CONFISCATION:
            case QUORUM_GENERIC:
            case QUORUM_REMOVE_ASSET:
                return formatCoin(parseParamValueToCoin(param, inputValue));

            case THRESHOLD_COMP_REQUEST:
            case THRESHOLD_REIMBURSEMENT:
            case THRESHOLD_CHANGE_PARAM:
            case THRESHOLD_ROLE:
            case THRESHOLD_CONFISCATION:
            case THRESHOLD_GENERIC:
            case THRESHOLD_REMOVE_ASSET:
                return formatToPercent(parsePercentStringToDouble(inputValue));

            case RECIPIENT_BTC_ADDRESS:
                InputValidator.ValidationResult validationResult = new BtcAddressValidator().validate(inputValue);
                if (validationResult.isValid)
                    return inputValue;
                else
                    throw new ValidationException(validationResult.errorMessage);

            case PHASE_UNDEFINED:
                throw new IllegalArgumentException("Unsupported param: " + param);
            case PHASE_PROPOSAL:
            case PHASE_BREAK1:
            case PHASE_BLIND_VOTE:
            case PHASE_BREAK2:
            case PHASE_VOTE_REVEAL:
            case PHASE_BREAK3:
            case PHASE_RESULT:
                return Integer.toString(parseParamValueToBlocks(param, inputValue));
            default:
                throw new IllegalArgumentException("Unsupported param: " + param);
        }
    }
}
