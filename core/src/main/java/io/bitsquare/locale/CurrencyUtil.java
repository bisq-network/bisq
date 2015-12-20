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
    transient private static final Logger log = LoggerFactory.getLogger(CurrencyUtil.class);

    private static List<TradeCurrency> allSortedCurrencies = createAllSortedCurrenciesList();

    public static List<TradeCurrency> getAllSortedCurrencies() {
        return allSortedCurrencies;
    }

    // We add all currencies supported by payment methods
    private static List<TradeCurrency> createAllSortedCurrenciesList() {
        Set<TradeCurrency> set = new HashSet<>();

        // Sepa: EUR at first place
        set.addAll(getSortedSepaCurrencyCodes());

        // PerfectMoney: 
        set.add(new TradeCurrency("USD"));

        // Alipay: 
        set.add(new TradeCurrency("CNY"));

        // OKPay: We want to maintain the order so we don't use a Set but add items if nto already in list
        getAllOKPayCurrencies().stream().forEach(e -> set.add(e));

        // Swish: it is already added by Sepa

        // for printing out all codes
         /* String res;
        result.stream().forEach(e -> {
            res += "list.add(new FiatCurrency(\""+e.code+"\"));\n";
        });
        log.debug(res);*/


        List<TradeCurrency> list = getAllManuallySortedFiatCurrencies();

        // check if the list derived form the payment methods is containing exactly the same as our manually sorted one

        List<String> list1 = set.stream().map(e -> e.code).collect(Collectors.toList());
        list1.sort((a, b) -> a.compareTo(b));
        List<String> list2 = list.stream().map(e -> e.code).collect(Collectors.toList());
        list2.sort((a, b) -> a.compareTo(b));

        if (list1.size() != list2.size()) {
            log.error("manually defined currencies are not matching currencies derived form our payment methods");
            log.error("list1 " + list1.toString());
            log.error("list2 " + list2.toString());
        }

        if (!list1.toString().equals(list2.toString())) {
            log.error("List derived form the payment methods is not matching exactly the same as our manually sorted one");
            log.error("list1 " + list1.toString());
            log.error("list2 " + list2.toString());
        }

        // Blockchain
        getSortedCryptoCurrencies().stream().forEach(e -> list.add(e));

        return list;
    }

    private static List<TradeCurrency> getAllManuallySortedFiatCurrencies() {
        List<TradeCurrency> list = new ArrayList<>();
        list.add(new FiatCurrency("EUR"));
        list.add(new FiatCurrency("USD"));
        list.add(new FiatCurrency("GBP"));
        list.add(new FiatCurrency("CNY"));
        list.add(new FiatCurrency("HKD"));
        list.add(new FiatCurrency("CHF"));
        list.add(new FiatCurrency("JPY"));
        list.add(new FiatCurrency("CAD"));
        list.add(new FiatCurrency("AUD"));
        list.add(new FiatCurrency("NZD"));
        list.add(new FiatCurrency("ZAR"));
        list.add(new FiatCurrency("RUB"));

        list.add(new FiatCurrency("SEK"));
        list.add(new FiatCurrency("NOK"));
        list.add(new FiatCurrency("DKK"));
        list.add(new FiatCurrency("ISK"));

        list.add(new FiatCurrency("PLN"));
        list.add(new FiatCurrency("CZK"));
        list.add(new FiatCurrency("TRY"));

        list.add(new FiatCurrency("BGN"));
        list.add(new FiatCurrency("HRK"));
        list.add(new FiatCurrency("HUF"));
        list.add(new FiatCurrency("RON"));

        return list;
    }

    /**
     * @return Sorted list of sepa currencies with EUR as first item
     */
    public static Set<TradeCurrency> getSortedSepaCurrencyCodes() {
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

    public static List<TradeCurrency> getSortedCryptoCurrencies() {
        final List<TradeCurrency> result = new ArrayList<>();
        result.add(new CryptoCurrency("ETH", "Ethereum"));
        result.add(new CryptoCurrency("LTC", "Litecoin"));
        result.add(new CryptoCurrency("NMC", "Namecoin"));
        // Unfortunately we cannot support CryptoNote coins yet as there is no way to proof the transaction. Payment ID helps only locate the tx but the 
        // arbitrator cannot see if the receiving key matches the receivers address. They might add support for exposing the tx key, but that is not 
        // implemented yet. To use the view key (also not available in GUI wallets) would reveal the complete wallet history for incoming payments, which is
        // not acceptable from pricavy point of view.
        // result.add(new CryptoCurrency("XMR", "Monero")); 
        // result.add(new CryptoCurrency("BCN", "Bytecoin"));
        result.add(new CryptoCurrency("DASH", "Dash"));
        result.add(new CryptoCurrency("ANC", "Anoncoin"));
        result.add(new CryptoCurrency("NBT", "NuBits"));
        result.add(new CryptoCurrency("NSR", "NuShares"));
        result.add(new CryptoCurrency("FAIR", "FairCoin"));
        result.add(new CryptoCurrency("PPC", "Peercoin"));
        result.add(new CryptoCurrency("XPM", "Primecoin"));
        result.add(new CryptoCurrency("DOGE", "Dogecoin"));
        result.add(new CryptoCurrency("NXT", "Nxt"));
        result.add(new CryptoCurrency("BTS", "BitShares"));
        result.add(new CryptoCurrency("XCP", "Counterparty"));
        result.add(new CryptoCurrency("XRP", "Ripple"));
        result.add(new CryptoCurrency("STR", "Stellar"));

        return result;
    }

    public static boolean isCryptoNoteCoin(String currencyCode) {
        return currencyCode.equals("XMR") || currencyCode.equals("BCN");
    }

    public static FiatCurrency getCurrencyByCountryCode(String countryCode) {
        // java 1.8.8_0_20 reports wrong currency (Lita instead of EUR)
        if (!countryCode.equals("LT"))
            return new FiatCurrency(Currency.getInstance(new Locale(LanguageUtil.getDefaultLanguage(), countryCode)).getCurrencyCode());
        else {
            return new FiatCurrency("EUR");
        }
    }


    public static String getNameByCode(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode).getDisplayName(Preferences.getDefaultLocale());
        } catch (Throwable t) {
            // Seems that it is a crypto currency
            return getSortedCryptoCurrencies().stream().filter(e -> e.getCode().equals(currencyCode)).findFirst().get().getCodeAndName();
        }
    }

    public static TradeCurrency getDefaultTradeCurrency() {
        return Preferences.getDefaultTradeCurrency();
    }

}
