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
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.locale.GlobalSettings;
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
import java.text.NumberFormat;

import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BsqFormatter extends BSFormatter {
    @SuppressWarnings("PointlessBooleanExpression")
    private static final boolean useBsqAddressFormat = true || !DevEnv.isDevMode();
    private final String prefix = "B";
    private DecimalFormat amountFormat;
    private DecimalFormat marketCapFormat;
    private final MonetaryFormat btcCoinFormat;

    @Inject
    public BsqFormatter() {
        super();

        GlobalSettings.localeProperty().addListener((observable, oldValue, newValue) -> setFormatter(newValue));
        setFormatter(GlobalSettings.getLocale());

        btcCoinFormat = super.coinFormat;

        final String baseCurrencyCode = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode();
        switch (baseCurrencyCode) {
            case "BTC":
                coinFormat = new MonetaryFormat().shift(6).code(6, "BSQ").minDecimals(2);
                break;
            default:
                throw new RuntimeException("baseCurrencyCode not defined. baseCurrencyCode=" + baseCurrencyCode);
        }

        amountFormat.setMinimumFractionDigits(2);
    }

    private void setFormatter(Locale locale) {
        amountFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        amountFormat.setMinimumFractionDigits(2);
        amountFormat.setMaximumFractionDigits(2);

        marketCapFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        marketCapFormat = new DecimalFormat();
        marketCapFormat.setMaximumFractionDigits(0);
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

    public String formatBSQSatoshis(long satoshi) {
        return super.formatCoin(satoshi, coinFormat);
    }

    public String formatBSQSatoshisWithCode(long satoshi) {
        return super.formatCoinWithCode(satoshi, coinFormat);
    }

    public String formatBTCSatoshis(long satoshi) {
        return super.formatCoin(satoshi, btcCoinFormat);
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

    public void validateBtcInput(String input) throws ProposalValidationException {
        validateCoinInput(input, btcCoinFormat);
    }

    public void validateBsqInput(String input) throws ProposalValidationException {
        validateCoinInput(input, this.coinFormat);
    }

    private void validateCoinInput(String input, MonetaryFormat coinFormat) throws ProposalValidationException {
        try {
            coinFormat.parse(cleanDoubleInput(input));
        } catch (Throwable t) {
            throw new ProposalValidationException("Invalid format for a " + coinFormat.code() + " value");
        }
    }

    public String formatParamValue(Param param, String value) {
        switch (param.getParamType()) {
            case UNDEFINED:
                // In case we add a new param old clients will not know that enum and fall back to UNDEFINED.
                return Res.get("shared.na");
            case BSQ:
                return formatCoinWithCode(parseToCoin(value));
            case BTC:
                return formatBTCWithCode(parseToBTC(value));
            case PERCENT:
                return formatToPercentWithSymbol(parsePercentStringToDouble(value));
            case BLOCK:
                return Res.get("dao.param.blocks", Integer.parseInt(value));
            case ADDRESS:
                return value;
            default:
                log.warn("Param type {} not handled in switch case at formatParamValue", param.getParamType());
                return Res.get("shared.na");
        }
    }

    public Coin parseParamValueToCoin(Param param, String inputValue) {
        switch (param.getParamType()) {
            case BSQ:
                return parseToCoin(inputValue);
            case BTC:
                return parseToBTC(inputValue);
            default:
                throw new IllegalArgumentException("Unsupported paramType. param: " + param);
        }
    }

    public int parseParamValueToBlocks(Param param, String inputValue) {
        switch (param.getParamType()) {
            case BLOCK:
                return Integer.parseInt(inputValue);
            default:
                throw new IllegalArgumentException("Unsupported paramType. param: " + param);
        }
    }

    public String parseParamValueToString(Param param, String inputValue) throws ProposalValidationException {
        switch (param.getParamType()) {
            case UNDEFINED:
                return Res.get("shared.na");
            case BSQ:
                return formatCoin(parseParamValueToCoin(param, inputValue));
            case BTC:
                return formatBTC(parseParamValueToCoin(param, inputValue));
            case PERCENT:
                return formatToPercent(parsePercentStringToDouble(inputValue));
            case BLOCK:
                return Integer.toString(parseParamValueToBlocks(param, inputValue));
            case ADDRESS:
                InputValidator.ValidationResult validationResult = new BtcAddressValidator().validate(inputValue);
                if (validationResult.isValid)
                    return inputValue;
                else
                    throw new ProposalValidationException(validationResult.errorMessage);
            default:
                log.warn("Param type {} not handled in switch case at parseParamValueToString", param.getParamType());
                return Res.get("shared.na");
        }
    }
}
