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

package bisq.core.util.coin;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.provider.price.MarketPrice;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.validation.BtcAddressValidator;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import java.util.Locale;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
public class BsqFormatter implements CoinFormatter {
    private final ImmutableCoinFormatter coinFormatter;

    protected int scale = 3;
    // We don't support localized formatting. Format is always using "." as decimal mark and no grouping separator.
    // Input of "," as decimal mark (like in german locale) will be replaced with ".".
    // Input of a group separator (1,123,45) lead to an validation error.
    // Note: BtcFormat was intended to be used, but it lead to many problems (automatic format to mBit,
    // no way to remove grouping separator). It seems to be not optimal for user input formatting.
    @Getter
    private MonetaryFormat monetaryFormat;
    private DecimalFormat amountFormat;
    private DecimalFormat marketCapFormat;
    private final MonetaryFormat btcCoinFormat;

    @Inject
    public BsqFormatter() {
        this.monetaryFormat = BisqEnvironment.getParameters().getMonetaryFormat();
        this.coinFormatter = new ImmutableCoinFormatter(BisqEnvironment.getParameters().getMonetaryFormat());

        GlobalSettings.localeProperty().addListener((observable, oldValue, newValue) -> switchToLocale(newValue));
        switchToLocale(GlobalSettings.getLocale());

        btcCoinFormat = monetaryFormat;

        final String baseCurrencyCode = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode();
        switch (baseCurrencyCode) {
            case "BTC":
                monetaryFormat = new MonetaryFormat().shift(6).code(6, "BSQ").minDecimals(2);
                break;
            default:
                throw new RuntimeException("baseCurrencyCode not defined. baseCurrencyCode=" + baseCurrencyCode);
        }

        amountFormat.setMinimumFractionDigits(2);
    }

    private void switchToLocale(Locale locale) {
        amountFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        amountFormat.setMinimumFractionDigits(2);
        amountFormat.setMaximumFractionDigits(2);

        marketCapFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        marketCapFormat = new DecimalFormat();
        marketCapFormat.setMaximumFractionDigits(0);
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
        return FormattingUtils.formatCoin(satoshi, monetaryFormat);
    }

    public String formatBSQSatoshisWithCode(long satoshi) {
        return FormattingUtils.formatCoinWithCode(satoshi, monetaryFormat);
    }

    public String formatBTCSatoshis(long satoshi) {
        return FormattingUtils.formatCoin(satoshi, btcCoinFormat);
    }

    public String formatBTCWithCode(long satoshi) {
        return FormattingUtils.formatCoinWithCode(satoshi, btcCoinFormat);
    }

    public String formatBTCWithCode(Coin coin) {
        return FormattingUtils.formatCoinWithCode(coin, btcCoinFormat);
    }

    private String formatBTC(Coin coin) {
        return FormattingUtils.formatCoin(coin.value, btcCoinFormat);
    }

    public Coin parseToBTC(String input) {
        return ParsingUtils.parseToCoin(input, btcCoinFormat);
    }

    public String formatParamValue(Param param, String value) {
        switch (param.getParamType()) {
            case UNDEFINED:
                // In case we add a new param old clients will not know that enum and fall back to UNDEFINED.
                return Res.get("shared.na");
            case BSQ:
                return formatCoinWithCode(ParsingUtils.parseToCoin(value, this));
            case BTC:
                return formatBTCWithCode(parseToBTC(value));
            case PERCENT:
                return FormattingUtils.formatToPercentWithSymbol(ParsingUtils.parsePercentStringToDouble(value));
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
                return ParsingUtils.parseToCoin(inputValue, this);
            case BTC:
                return parseToBTC(inputValue);
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
                return FormattingUtils.formatToPercent(ParsingUtils.parsePercentStringToDouble(inputValue));
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

    private int parseParamValueToBlocks(Param param, String inputValue) {
        switch (param.getParamType()) {
            case BLOCK:
                return Integer.parseInt(inputValue);
            default:
                throw new IllegalArgumentException("Unsupported paramType. param: " + param);
        }
    }

    public String formatCoin(Coin coin) {
        return coinFormatter.formatCoin(coin, -1);
    }

    @NotNull
    public String formatCoin(Coin coin, int decimalPlaces) {
        return coinFormatter.formatCoin(coin, decimalPlaces, false, 0);
    }

    public String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits) {
        return coinFormatter.formatCoin(coin, decimalPlaces, decimalAligned, maxNumberOfDigits);
    }

    public String formatCoinWithCode(Coin coin) {
        return coinFormatter.formatCoin(coin);
    }

    public String formatCoinWithCode(long value) {
        return coinFormatter.formatCoinWithCode(value);
    }
}
