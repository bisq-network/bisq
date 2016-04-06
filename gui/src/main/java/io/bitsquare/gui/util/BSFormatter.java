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

import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BSFormatter {
    private static final Logger log = LoggerFactory.getLogger(BSFormatter.class);

    private Locale locale = Preferences.getDefaultLocale();
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

    //  private String currencyCode = CurrencyUtil.getDefaultFiatCurrencyAsCode();

    // format is like: 1,00  never more then 2 decimals
    private final MonetaryFormat fiatFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);


    @Inject
    public BSFormatter() {
      /*  if (user.tradeCurrencyProperty().get() == null)
            setFiatCurrencyCode(CurrencyUtil.getDefaultFiatCurrencyAsCode());
        else if (user.tradeCurrencyProperty().get() != null)
            setFiatCurrencyCode(user.tradeCurrencyProperty().get().getCode());

        user.tradeCurrencyProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                setFiatCurrencyCode(newValue.getCode());
        });*/
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

  /*  public void setFiatCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        fiatFormat.code(0, currencyCode);
    }*/


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatCoin(Coin coin) {
        if (coin != null) {
            try {
                return coinFormat.noCode().format(coin).toString();
            } catch (Throwable t) {
                log.warn("Exception at formatBtc: " + t.toString());
                return "";
            }
        } else {
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
                log.warn("Exception at formatBtcWithCode: " + t.toString());
                return "";
            }
        } else {
            return "";
        }
    }

    public Coin parseToCoin(String input) {
        if (input != null && input.length() > 0) {
            try {
                return coinFormat.parse(cleanInput(input));
            } catch (Throwable t) {
                log.warn("Exception at parseToBtc: " + t.toString());
                return Coin.ZERO;
            }
        } else {
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
                log.warn("Exception at formatFiat: " + t.toString());
                return "";
            }
        } else {
            return "";
        }
    }

    public String formatFiatWithCode(Fiat fiat) {
        if (fiat != null) {
            try {
                //return fiatFormat.postfixCode().format(fiat).toString();
                return fiatFormat.noCode().format(fiat).toString() + " " + fiat.getCurrencyCode();
            } catch (Throwable t) {
                log.warn("Exception at formatFiatWithCode: " + t.toString());
                return "";
            }
        } else {
            return "";
        }
    }

    public String formatPriceWithCode(Fiat fiat) {
        return formatFiatWithCode(fiat) + "/BTC";
    }

    private Fiat parseToFiat(String input, String currencyCode) {
        if (input != null && input.length() > 0) {
            try {
                return Fiat.parseFiat(currencyCode, cleanInput(input));
            } catch (Exception e) {
                log.warn("Exception at parseToFiat: " + e.toString());
                return Fiat.valueOf(currencyCode, 0);
            }

        } else {
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

    public Fiat parseToFiatWith2Decimals(String input, String currencyCode) {
        if (input != null && input.length() > 0) {
            try {
                return parseToFiat(new BigDecimal(cleanInput(input)).setScale(2, BigDecimal.ROUND_HALF_UP).toString(), currencyCode);
            } catch (Throwable t) {
                log.warn("Exception at parseCoinTo4Decimals: " + t.toString());
                return Fiat.valueOf(currencyCode, 0);
            }

        }
        return Fiat.valueOf(currencyCode, 0);
    }

    public boolean hasFiatValidDecimals(String input, String currencyCode) {
        return parseToFiat(input, currencyCode).equals(parseToFiatWith2Decimals(input, currencyCode));
    }

    public String formatMarketPrice(double price) {
        return formatMarketPrice(price, 3);
    }

    public String formatMarketPrice(double price, int decimals) {
        DecimalFormat df = new DecimalFormat("#.#");
        df.setMaximumFractionDigits(decimals);
        return df.format(price);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////


    public String getDirection(Offer.Direction direction) {
        return getDirection(direction, false) + " bitcoin";
    }

    private String getDirection(Offer.Direction direction, boolean allUpperCase) {
        String result = (direction == Offer.Direction.BUY) ? "Buy" : "Sell";
        if (allUpperCase) {
            result = result.toUpperCase();
        }
        return result;
    }

    public String formatAmountWithMinAmount(Offer offer) {
        return formatCoin(offer.getAmount()) + " (" + formatCoin(offer.getMinAmount()) + ")";
    }

    public String formatVolumeWithMinVolumeWithCode(Offer offer) {
        return formatFiatWithCode(offer.getOfferVolume()) +
                " (" + formatFiatWithCode(offer.getMinOfferVolume()) + ")";
    }

    public String arbitratorAddressesToString(List<NodeAddress> nodeAddresses) {
        return nodeAddresses.stream().map(e -> e.getFullAddress()).collect(Collectors.joining(", "));
    }

    public String languageCodesToString(List<String> languageLocales) {
        return languageLocales.stream().map(LanguageUtil::getDisplayName).collect(Collectors.joining(", "));
    }

    public String formatDateTime(Date date) {
        if (date != null) {
            DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
            DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, locale);
            return dateFormatter.format(date) + " " + timeFormatter.format(date);
        } else {
            return "";
        }
    }

    public String formatDate(Date date) {
        if (date != null) {
            DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
            return dateFormatter.format(date);
        } else {
            return "";
        }
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

    public String formatDurationAsWords(long durationMillis) {
        return formatDurationAsWords(durationMillis, false);
    }

    public static String formatDurationAsWords(long durationMillis, boolean showSeconds) {
        String format;
        if (showSeconds)
            format = "d\' days, \'H\' hours, \'m\' minutes, \'s\' seconds\'";
        else
            format = "d\' days, \'H\' hours, \'m\' minutes\'";
        String duration = DurationFormatUtils.formatDuration(durationMillis, format);
        String tmp;
        duration = " " + duration;
        tmp = StringUtils.replaceOnce(duration, " 0 days", "");
        if (tmp.length() != duration.length()) {
            duration = tmp;
            tmp = StringUtils.replaceOnce(tmp, " 0 hours", "");
            if (tmp.length() != duration.length()) {
                tmp = StringUtils.replaceOnce(tmp, " 0 minutes", "");
                duration = tmp;
                if (tmp.length() != tmp.length()) {
                    duration = StringUtils.replaceOnce(tmp, " 0 seconds", "");
                }
            }
        }

        if (duration.length() != 0) {
            duration = duration.substring(1);
        }

        tmp = StringUtils.replaceOnce(duration, " 0 seconds", "");

        if (tmp.length() != duration.length()) {
            duration = tmp;
            tmp = StringUtils.replaceOnce(tmp, " 0 minutes", "");
            if (tmp.length() != duration.length()) {
                duration = tmp;
                tmp = StringUtils.replaceOnce(tmp, " 0 hours", "");
                if (tmp.length() != duration.length()) {
                    duration = StringUtils.replaceOnce(tmp, " 0 days", "");
                }
            }
        }

        duration = " " + duration;
        duration = StringUtils.replaceOnce(duration, " 1 seconds", " 1 second");
        duration = StringUtils.replaceOnce(duration, " 1 minutes", " 1 minute");
        duration = StringUtils.replaceOnce(duration, " 1 hours", " 1 hour");
        duration = StringUtils.replaceOnce(duration, " 1 days", " 1 day");
        return duration.trim();
    }


    public String booleanToYesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    public String formatBitcoinNetwork(BitcoinNetwork bitcoinNetwork) {
        switch (bitcoinNetwork) {
            case MAINNET:
                return "Mainnet";
            case TESTNET:
                return "Testnet";
            case REGTEST:
                return "Regtest";
            default:
                return "";
        }
    }

    public String getDirectionBothSides(Offer.Direction direction) {
        return direction == Offer.Direction.BUY ? "Offerer as bitcoin buyer / Taker as bitcoin seller" :
                "Offerer as bitcoin seller / Taker as bitcoin buyer";
    }

    public String getDirectionForBuyer(boolean isMyOffer) {
        return isMyOffer ? "You are buying bitcoin as offerer / Taker is selling bitcoin" :
                "You are buying bitcoin as taker / Offerer is selling bitcoin";
    }

    public String getDirectionForSeller(boolean isMyOffer) {
        return isMyOffer ? "You are selling bitcoin as offerer / Taker is buying bitcoin" :
                "You are selling bitcoin as taker / Offerer is buying bitcoin";
    }

    public String getDirectionForTakeOffer(Offer.Direction direction) {
        return direction == Offer.Direction.BUY ? "You are selling bitcoin (by taking an offer from someone who wants to buy bitcoin)" :
                "You are buying bitcoin (by taking an offer from someone who wants to sell bitcoin)";
    }

    public String getOfferDirectionForCreateOffer(Offer.Direction direction) {
        return direction == Offer.Direction.BUY ? "You are creating an offer for buying bitcoin" :
                "You are creating an offer for selling bitcoin";
    }

    public String getRole(boolean isBuyerOffererAndSellerTaker, boolean isOfferer) {
        if (isBuyerOffererAndSellerTaker)
            return isOfferer ? "Buyer (offerer)" : "Seller (taker)";
        else
            return isOfferer ? "Seller (offerer)" : "Buyer (taker)";
    }

    public String formatBytes(int bytes) {
        double kb = 1024;
        double mb = kb * kb;
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        if (bytes < kb)
            return bytes + " bytes";
        else if (bytes < mb)
            return decimalFormat.format(bytes / kb) + " KB";
        else
            return decimalFormat.format(bytes / mb) + " MB";
    }

}
