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

package io.bitsquare.locale;

import io.bitsquare.user.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class CurrencyUtil {
    private static final Logger log = LoggerFactory.getLogger(CurrencyUtil.class);

    private static final List<FiatCurrency> allSortedFiatCurrencies = createAllSortedFiatCurrenciesList();

    private static List<FiatCurrency> createAllSortedFiatCurrenciesList() {
        Set<FiatCurrency> set = CountryUtil.getAllCountries().stream()
                .map(country -> getCurrencyByCountryCode(country.code))
                .collect(Collectors.toSet());
        List<FiatCurrency> list = new ArrayList<>(set);
        list.sort(TradeCurrency::compareTo);
        return list;
    }

    public static List<FiatCurrency> getAllSortedFiatCurrencies() {
        return allSortedFiatCurrencies;
    }

    public static List<FiatCurrency> getAllMainFiatCurrencies() {
        List<FiatCurrency> list = new ArrayList<>();
        // Top traded currencies
        list.add(new FiatCurrency("USD"));
        list.add(new FiatCurrency("EUR"));
        list.add(new FiatCurrency("GBP"));
        list.add(new FiatCurrency("CAD"));
        list.add(new FiatCurrency("AUD"));
        list.add(new FiatCurrency("RUB"));
        list.add(new FiatCurrency("INR"));

        TradeCurrency defaultTradeCurrency = getDefaultTradeCurrency();
        FiatCurrency defaultFiatCurrency = defaultTradeCurrency instanceof FiatCurrency ? (FiatCurrency) defaultTradeCurrency : null;
        if (defaultFiatCurrency != null && list.contains(defaultFiatCurrency)) {
            list.remove(defaultTradeCurrency);
            list.add(0, defaultFiatCurrency);
        }
        return list;
    }

    private static final List<CryptoCurrency> allSortedCryptoCurrencies = createAllSortedCryptoCurrenciesList();

    public static List<CryptoCurrency> getAllSortedCryptoCurrencies() {
        return allSortedCryptoCurrencies;
    }

    public static List<CryptoCurrency> getMainCryptoCurrencies() {
        final List<CryptoCurrency> result = new ArrayList<>();
        result.add(new CryptoCurrency("ETH", "Ethereum"));
        result.add(new CryptoCurrency("LTC", "Litecoin"));
        result.add(new CryptoCurrency("DASH", "Dash"));
        result.add(new CryptoCurrency("SDC", "ShadowCash"));
        result.add(new CryptoCurrency("NMC", "Namecoin"));
        result.add(new CryptoCurrency("NBT", "NuBits"));
        result.add(new CryptoCurrency("FAIR", "FairCoin"));
        result.add(new CryptoCurrency("DOGE", "Dogecoin"));
        result.add(new CryptoCurrency("NXT", "Nxt"));
        result.add(new CryptoCurrency("BTS", "BitShares"));
        return result;
    }

    public static List<CryptoCurrency> createAllSortedCryptoCurrenciesList() {
        final List<CryptoCurrency> result = new ArrayList<>();
        result.add(new CryptoCurrency("ETH", "Ethereum"));
        result.add(new CryptoCurrency("LTC", "Litecoin"));
        result.add(new CryptoCurrency("NMC", "Namecoin"));
        result.add(new CryptoCurrency("DASH", "Dash"));
        result.add(new CryptoCurrency("SDC", "ShadowCash"));
        result.add(new CryptoCurrency("NBT", "NuBits"));
        result.add(new CryptoCurrency("NSR", "NuShares"));
        result.add(new CryptoCurrency("PPC", "Peercoin"));
        result.add(new CryptoCurrency("XPM", "Primecoin"));
        result.add(new CryptoCurrency("FAIR", "FairCoin"));
        result.add(new CryptoCurrency("SC", "Siacoin"));
        result.add(new CryptoCurrency("SJCX", "StorjcoinX"));
        result.add(new CryptoCurrency("GEMZ", "Gemz"));
        result.add(new CryptoCurrency("DOGE", "Dogecoin"));
        result.add(new CryptoCurrency("BLK", "Blackcoin"));
        result.add(new CryptoCurrency("FCT", "Factom"));
        result.add(new CryptoCurrency("NXT", "Nxt"));
        result.add(new CryptoCurrency("BTS", "BitShares"));
        result.add(new CryptoCurrency("XCP", "Counterparty"));
        result.add(new CryptoCurrency("XRP", "Ripple"));
        result.add(new CryptoCurrency("XEM", "NEM"));
        result.add(new CryptoCurrency("ANTI", "Anti.cash"));
        result.add(new CryptoCurrency("VPN", "VPNCoin"));
        result.add(new CryptoCurrency("MAID", "MaidSafeCoin"));
        result.add(new CryptoCurrency("YBC", "YbCoin"));
        
                
        // Unfortunately we cannot support CryptoNote coins yet as there is no way to proof the transaction. Payment ID helps only locate the tx but the 
        // arbitrator cannot see if the receiving key matches the receivers address. They might add support for exposing the tx key, but that is not 
        // implemented yet. To use the view key (also not available in GUI wallets) would reveal the complete wallet history for incoming payments, which is
        // not acceptable from privacy point of view.
        // result.add(new CryptoCurrency("XMR", "Monero")); 
        // result.add(new CryptoCurrency("BCN", "Bytecoin"));
        return result;
    }


    /**
     * @return Sorted list of SEPA currencies with EUR as first item
     */
    private static Set<TradeCurrency> getSortedSEPACurrencyCodes() {
        return CountryUtil.getAllSepaCountries().stream()
                .map(country -> getCurrencyByCountryCode(country.code))
                .collect(Collectors.toSet());
    }

    // At OKPay you can exchange internally those currencies
    public static List<TradeCurrency> getAllOKPayCurrencies() {
        return new ArrayList<>(Arrays.asList(
                new FiatCurrency("EUR"),
                new FiatCurrency("USD"),
                new FiatCurrency("GBP"),
                new FiatCurrency("CHF"),
                new FiatCurrency("RUB"),
                new FiatCurrency("PLN"),
                new FiatCurrency("JPY"),
                new FiatCurrency("CAD"),
                new FiatCurrency("AUD"),
                new FiatCurrency("CZK"),
                new FiatCurrency("NOK"),
                new FiatCurrency("SEK"),
                new FiatCurrency("DKK"),
                new FiatCurrency("HRK"),
                new FiatCurrency("HUF"),
                new FiatCurrency("NZD"),
                new FiatCurrency("RON"),
                new FiatCurrency("TRY"),
                new FiatCurrency("ZAR"),
                new FiatCurrency("HKD"),
                new FiatCurrency("CNY")
        ));
    }

    public static boolean isFiatCurrency(String currencyCode) {
        return !(isCryptoCurrency(currencyCode)) && Currency.getInstance(currencyCode) != null;
    }

    public static Optional<FiatCurrency> getFiatCurrency(String currencyCode) {
        return allSortedFiatCurrencies.stream().filter(e -> e.getCode().equals(currencyCode)).findAny();
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isCryptoCurrency(String currencyCode) {
        return getAllSortedCryptoCurrencies().stream().filter(e -> e.getCode().equals(currencyCode)).findAny().isPresent();
    }

    public static Optional<CryptoCurrency> getCryptoCurrency(String currencyCode) {
        return getAllSortedCryptoCurrencies().stream().filter(e -> e.getCode().equals(currencyCode)).findAny();
    }

    public static boolean isCryptoNoteCoin(String currencyCode) {
        return currencyCode.equals("XMR") || currencyCode.equals("BCN");
    }

    public static FiatCurrency getCurrencyByCountryCode(String countryCode) {
        return new FiatCurrency(Currency.getInstance(new Locale(LanguageUtil.getDefaultLanguage(), countryCode)).getCurrencyCode());
    }


    public static String getNameByCode(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode).getDisplayName(Preferences.getDefaultLocale());
        } catch (Throwable t) {
            // Seems that it is a cryptocurrency
            return getAllSortedCryptoCurrencies().stream().filter(e -> e.getCode().equals(currencyCode)).findFirst().get().getName();
        }
    }

    public static TradeCurrency getDefaultTradeCurrency() {
        return Preferences.getDefaultTradeCurrency();
    }

}
