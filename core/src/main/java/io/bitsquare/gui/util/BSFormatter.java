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

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.offer.Offer;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;

import java.math.BigDecimal;

import java.text.DateFormat;
import java.text.DecimalFormat;

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
 * <p/>
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
    private MonetaryFormat coinFormat = MonetaryFormat.BTC.repeatOptionalDecimals(2, 2);

    private String currencyCode = CurrencyUtil.getDefaultCurrencyAsCode();

    // format is like: 1,00  never more then 2 decimals
    private final MonetaryFormat fiatFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0).code(0, currencyCode);


    @Inject
    public BSFormatter(User user) {
        if (user.currentFiatAccountProperty().get() == null)
            setFiatCurrencyCode(CurrencyUtil.getDefaultCurrencyAsCode());
        else if (user.currentFiatAccountProperty().get() != null)
            setFiatCurrencyCode(user.currentFiatAccountProperty().get().getCurrencyCode());

        user.currentFiatAccountProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                setFiatCurrencyCode(newValue.getCurrencyCode());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Config
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void useMilliBitFormat(boolean useMilliBit) {
        this.useMilliBit = useMilliBit;
        coinFormat = getMonetaryFormat();
        scale = useMilliBit ? 0 : 3;
    }

    /**
     * Note that setting the locale does not set the currency as it might be independent.
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    private MonetaryFormat getMonetaryFormat() {
        if (useMilliBit)
            return MonetaryFormat.MBTC;
        else
            return MonetaryFormat.BTC.repeatOptionalDecimals(2, 2);
    }

    public void setFiatCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        fiatFormat.code(0, currencyCode);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatCoin(Coin coin) {
        if (coin != null) {
            try {
                return coinFormat.noCode().format(coin).toString();
            } catch (Throwable t) {
                if (coin != null)
                    log.warn("Exception at formatBtc: " + t.toString());
                return "";
            }
        }
        else {
            return "";
        }
    }

    public String formatCoinWithCode(Coin coin) {
        if (coin != null) {
            try {
                // we don't use the code feature from coinFormat as it does automatic switching between mBTC and BTC and
                // pre and post fixing
                return coinFormat.postfixCode().format(coin).toString();
            } catch (Throwable t) {
                if (coin != null)
                    log.warn("Exception at formatBtcWithCode: " + t.toString());
                return "";
            }
        }
        else {
            return "";
        }
    }

    public Coin parseToCoin(String input) {
        if (input != null) {
            try {
                return coinFormat.parse(cleanInput(input));
            } catch (Throwable t) {
                if (input != null && input.length() > 0)
                    log.warn("Exception at parseToBtc: " + t.toString());
                return Coin.ZERO;
            }
        }
        else {
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
            if (input != null && input.length() > 0)
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
        if (fiat != null) {
            try {
                return fiatFormat.noCode().format(fiat).toString();
            } catch (Throwable t) {
                if (fiat != null)
                    log.warn("Exception at formatFiat: " + t.toString());
                return "";
            }
        }
        else {
            return "";
        }
    }

    public String formatFiatWithCode(Fiat fiat) {
        if (fiat != null) {
            try {
                return fiatFormat.postfixCode().format(fiat).toString();
            } catch (Throwable t) {
                if (fiat != null)
                    log.warn("Exception at formatFiatWithCode: " + t.toString());
                return "";
            }
        }
        else {
            return "";
        }
    }

    public Fiat parseToFiat(String input) {
        if (input != null) {
            try {
                return Fiat.parseFiat(currencyCode, cleanInput(input));
            } catch (Exception e) {
                if (input != null && input.length() > 0)
                    log.warn("Exception at parseToFiat: " + e.toString());
                return Fiat.valueOf(currencyCode, 0);
            }

        }
        else {
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
        if (input != null) {
            try {
                return parseToFiat(new BigDecimal(cleanInput(input)).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
            } catch (Throwable t) {
                if (input != null && input.length() > 0)
                    log.warn("Exception at parseCoinTo4Decimals: " + t.toString());
                return Fiat.valueOf(currencyCode, 0);
            }

        }
        return Fiat.valueOf(currencyCode, 0);
    }

    public boolean hasFiatValidDecimals(String input) {
        return parseToFiat(input).equals(parseToFiatWith2Decimals(input));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////


    public String formatDirection(Offer.Direction direction) {
        return formatDirection(direction, true);
    }

    public String formatDirection(Offer.Direction direction, boolean allUpperCase) {
        String result = (direction == Offer.Direction.BUY) ? "Buy" : "Sell";
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

    public String languageCodesToString(List<String> languageLocales) {
        return languageLocales.stream().map(e -> LanguageUtil.getDisplayName(e)).collect(Collectors.joining(", "));
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

    public String formatToPercent(double value) {
        DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(locale);
        decimalFormat.setMinimumFractionDigits(1);
        decimalFormat.setMaximumFractionDigits(1);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat.format(value * 100.0) + " %";
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
