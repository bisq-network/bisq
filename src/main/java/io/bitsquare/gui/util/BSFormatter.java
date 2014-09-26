/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.CoinFormat;
import com.google.bitcoin.utils.Fiat;

import java.math.BigDecimal;

import java.text.DateFormat;
import java.text.DecimalFormat;

import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO convert to non static

/**
 * Central point for formatting and input parsing.
 * <p>
 * Note that we never use for text input values any coin or currency symbol or code.
 * BtcFormat does not support
 */
public class BSFormatter {
    private static final Logger log = LoggerFactory.getLogger(BSFormatter.class);

    private Locale locale = Locale.getDefault();
    private boolean useMilliBit;
    private int scale = 3;

    // Format use 2 min decimal places and 2 more optional: 1.00 or 1.0010  
    // There are not more then 4 decimals allowed.
    // We don't support localized formatting. Format is always using "." as decimal mark and no grouping separator.
    // Input of "," as decimal mark (like in german locale) will be replaced with ".".
    // Input of a group separator (1,123,45) lead to an validation error.
    // Note: BtcFormat was intended to be used, but it lead to many problems (automatic format to mBit, 
    // no way to remove grouping separator). It seems to be not optimal for user input formatting.
    private CoinFormat coinFormat = CoinFormat.BTC.repeatOptionalDecimals(2, 1);

    private String currencyCode = Currency.getInstance(Locale.getDefault()).getCurrencyCode();

    // format is like: 1,00  never more then 2 decimals 
    private final CoinFormat fiatFormat = CoinFormat.FIAT.repeatOptionalDecimals(0, 0).code(0, currencyCode);


    @Inject
    public BSFormatter(User user) {
        if (user.currentBankAccountProperty().get() == null)
            setFiatCurrencyCode(Currency.getInstance(Locale.getDefault()).getCurrencyCode());
        else if (user.currentBankAccountProperty().get() != null)
            setFiatCurrencyCode(user.currentBankAccountProperty().get().getCurrency().getCurrencyCode());

        user.currentBankAccountProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                setFiatCurrencyCode(newValue.getCurrency().getCurrencyCode());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Config
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void useMilliBitFormat(boolean useMilliBit) {
        this.useMilliBit = useMilliBit;
        coinFormat = getCoinFormat();
        scale = useMilliBit ? 0 : 3;
    }

    /**
     * Note that setting the locale does not set the currency as it might be independent.
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    private CoinFormat getCoinFormat() {
        if (useMilliBit)
            return CoinFormat.MBTC.repeatOptionalDecimals(2, 1);
        else
            return CoinFormat.BTC.repeatOptionalDecimals(2, 1);
    }

    public void setFiatCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        fiatFormat.code(0, currencyCode);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatCoin(Coin coin) {
        try {
            return coinFormat.noCode().format(coin).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatBtc: " + t.toString());
            return "";
        }
    }

    public String formatCoinWithCode(Coin coin) {
        try {
            // we don't use the code feature from coinFormat as it does automatic switching between mBTC and BTC and 
            // pre and post fixing
            return coinFormat.postfixCode().format(coin).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatBtcWithCode: " + t.toString());
            return "";
        }
    }


    public Coin parseToCoin(String input) {
        try {
            return coinFormat.parse(cleanInput(input));
        } catch (Throwable t) {
            log.warn("Exception at parseToBtc: " + t.toString());
            return Coin.ZERO;
        }
    }

    /**
     * Converts to a coin with max. 4 decimal places. Last place gets rounded.
     * 0.01234 -> 0.0123
     * 0.01235 -> 0.0124
     *
     * @param input
     * @return
     */
    public Coin parseToCoinWith4Decimals(String input) {
        try {
            return Coin.valueOf(new BigDecimal(parseToCoin(cleanInput(input)).value).setScale(-scale - 1,
                    BigDecimal.ROUND_HALF_UP).setScale(scale + 1).toBigInteger().longValue());
        } catch (Throwable t) {
            log.warn("Exception at parseToCoinWith4Decimals: " + t.toString());
            return Coin.ZERO;
        }
    }

    public boolean hasBtcValidDecimals(String input) {
        return parseToCoin(input).equals(parseToCoinWith4Decimals(input));
    }

    /**
     * Transform a coin with the properties defined in the format (used to reduce decimal places)
     *
     * @param coin The coin which should be transformed
     * @return The transformed coin
     */
    public Coin reduceTo4Decimals(Coin coin) {
        return parseToCoin(formatCoin(coin));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // FIAT
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatFiat(Fiat fiat) {
        try {
            return fiatFormat.noCode().format(fiat).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatFiat: " + t.toString());
            return "";
        }
    }

    public String formatFiatWithCode(Fiat fiat) {
        try {
            return fiatFormat.postfixCode().format(fiat).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatFiatWithCode: " + t.toString());
            return "";
        }
    }

    public Fiat parseToFiat(String input) {
        try {
            return Fiat.parseFiat(currencyCode, cleanInput(input));
        } catch (Exception e) {
            return Fiat.valueOf(currencyCode, 0);
        }
    }

    /**
     * Converts to a fiat with max. 2 decimal places. Last place gets rounded.
     * 0.234 -> 0.23
     * 0.235 -> 0.24
     *
     * @param input
     * @return
     */
    public Fiat parseToFiatWith2Decimals(String input) {
        try {
            return parseToFiat(new BigDecimal(cleanInput(input)).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
        } catch (Throwable t) {
            log.warn("Exception at parseCoinTo4Decimals: " + t.toString());
            return Fiat.valueOf(currencyCode, 0);
        }

    }

    public boolean hasFiatValidDecimals(String input) {
        return parseToFiat(input).equals(parseToFiatWith2Decimals(input));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////


    public String formatDirection(Direction direction) {
        return formatDirection(direction, true);
    }

    public String formatDirection(Direction direction, boolean allUpperCase) {
        String result = (direction == Direction.BUY) ? "Buy" : "Sell";
        if (allUpperCase) {
            result = result.toUpperCase();
        }
        return result;
    }

    public String formatAmountWithMinAmount(Offer offer) {
        return formatCoin(offer.getAmount()) + " (" + formatCoin(offer.getMinAmount()) + ")";
    }

    public String formatVolumeWithMinVolume(Offer offer) {
        return formatFiat(offer.getOfferVolume()) +
                " (" + formatFiat(offer.getMinOfferVolume()) + ")";
    }

    public String countryLocalesToString(List<Country> countries) {
        return countries.stream().map(Country::getName).collect(Collectors.joining(", "));
    }

    public String arbitratorsToString(List<Arbitrator> arbitrators) {
        return arbitrators.stream().map(Arbitrator::getName).collect(Collectors.joining(", "));
    }

    public String languageLocalesToString(List<Locale> languageLocales) {
        return languageLocales.stream().map(e -> e.getDisplayLanguage()).collect(Collectors.joining(", "));
    }

    public String arbitrationMethodsToString(List<Arbitrator.METHOD> methods) {
        return methods.stream().map(e -> BSResources.get(e.toString())).collect(Collectors.joining(", "));
    }

    public String arbitrationIDVerificationsToString(List<Arbitrator.ID_VERIFICATION> items) {
        return items.stream().map(e -> BSResources.get(e.toString())).collect(Collectors.joining(", "));
    }

    public String mnemonicCodeToString(List<String> mnemonicCode) {
        return mnemonicCode.stream().collect(Collectors.joining(" "));
    }


    public String formatDateTime(Date date) {
        DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
        DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, locale);
        return dateFormatter.format(date) + " " + timeFormatter.format(date);
    }

    public String formatCollateralPercent(long collateral) {
        DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(locale);
        decimalFormat.setMinimumFractionDigits(1);
        decimalFormat.setMaximumFractionDigits(1);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat.format(collateral / 10) + " %";
    }

    public String formatToPercent(double value) {
        DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(locale);
        decimalFormat.setMinimumFractionDigits(1);
        decimalFormat.setMaximumFractionDigits(1);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat.format(value / 100) + " %";
    }

    private String cleanInput(String input) {
        input = input.replace(",", ".");
        // don't use String.valueOf(Double.parseDouble(input)) as return value as it gives scientific 
        // notation (1.0E-6) which screw up coinFormat.parse
        //noinspection ResultOfMethodCallIgnored
        Double.parseDouble(input);
        return input;
    }
}
