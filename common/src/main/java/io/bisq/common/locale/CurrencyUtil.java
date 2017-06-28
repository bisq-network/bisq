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

package io.bisq.common.locale;

import io.bisq.common.GlobalSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CurrencyUtil {
    private static String baseCurrencyCode = "BTC";

    public static void setBaseCurrencyCode(String baseCurrencyCode) {
        CurrencyUtil.baseCurrencyCode = baseCurrencyCode;
    }

    private static List<FiatCurrency> allSortedFiatCurrencies;

    private static List<FiatCurrency> createAllSortedFiatCurrenciesList() {
        Set<FiatCurrency> set = CountryUtil.getAllCountries().stream()
                .map(country -> getCurrencyByCountryCode(country.code))
                .collect(Collectors.toSet());
        List<FiatCurrency> list = new ArrayList<>(set);
        list.sort(TradeCurrency::compareTo);
        return list;
    }

    public static List<FiatCurrency> getAllSortedFiatCurrencies() {
        if (Objects.isNull(allSortedFiatCurrencies)) {
            allSortedFiatCurrencies = createAllSortedFiatCurrenciesList();
        }
        return allSortedFiatCurrencies;
    }


    public static List<FiatCurrency> getMainFiatCurrencies() {
        TradeCurrency defaultTradeCurrency = getDefaultTradeCurrency();
        List<FiatCurrency> list = new ArrayList<>();
        // Top traded currencies
        list.add(new FiatCurrency("USD"));
        list.add(new FiatCurrency("EUR"));
        list.add(new FiatCurrency("GBP"));
        list.add(new FiatCurrency("CAD"));
        list.add(new FiatCurrency("AUD"));
        list.add(new FiatCurrency("RUB"));
        list.add(new FiatCurrency("INR"));

        list.sort(TradeCurrency::compareTo);

        FiatCurrency defaultFiatCurrency = defaultTradeCurrency instanceof FiatCurrency ? (FiatCurrency) defaultTradeCurrency : null;
        if (defaultFiatCurrency != null && list.contains(defaultFiatCurrency)) {
            //noinspection SuspiciousMethodCalls
            list.remove(defaultTradeCurrency);
            list.add(0, defaultFiatCurrency);
        }
        return list;
    }

    private static List<CryptoCurrency> allSortedCryptoCurrencies;

    public static List<CryptoCurrency> getAllSortedCryptoCurrencies() {
        if (allSortedCryptoCurrencies == null)
            allSortedCryptoCurrencies = createAllSortedCryptoCurrenciesList();
        return allSortedCryptoCurrencies;
    }

    // Don't make a PR for adding a coin but follow the steps described here: 
    // https://forum.bisq.io/t/how-to-add-your-favorite-altcoin/
    public static List<CryptoCurrency> createAllSortedCryptoCurrenciesList() {
        final List<CryptoCurrency> result = new ArrayList<>();

        result.add(new CryptoCurrency("AIB", "Advanced Internet Blocks"));
        result.add(new CryptoCurrency("ANC", "Anoncoin"));
        result.add(new CryptoCurrency("ANTI", "Anti"));
        result.add(new CryptoCurrency("ARCO", "AquariusCoin"));
        result.add(new CryptoCurrency("ARG", "Argentum"));
        result.add(new CryptoCurrency("REP", "Augur", true));
        result.add(new CryptoCurrency("BATL", "Battlestars"));
        result.add(new CryptoCurrency("BIGUP", "BigUp"));
        // result.add(new CryptoCurrency("BSQ", "Bisq Token"));
        if (!baseCurrencyCode.equals("BTC"))
            result.add(new CryptoCurrency("BTC", "Bitcoin"));
        result.add(new CryptoCurrency("BITAUD", "BitAUD", true));
        result.add(new CryptoCurrency("BITCHF", "BitCHF", true));
        result.add(new CryptoCurrency("BITCNY", "BitCNY", true));
        result.add(new CryptoCurrency("BITEUR", "BitEUR", true));
        result.add(new CryptoCurrency("BITGBP", "BitGBP", true));
        result.add(new CryptoCurrency("BITHKD", "BitHKD", true));
        result.add(new CryptoCurrency("BITNZD", "BitNZD", true));
        result.add(new CryptoCurrency("BITSEK", "BitSEK", true));
        result.add(new CryptoCurrency("BITSGD", "BitSGD", true));
        result.add(new CryptoCurrency("SYNQ", "BitSYNQ"));
        result.add(new CryptoCurrency("BTS", "BitShares"));
        result.add(new CryptoCurrency("BITUSD", "BitUSD", true));
        result.add(new CryptoCurrency("BLK", "Blackcoin"));
        result.add(new CryptoCurrency("BURST", "Burstcoin"));
        result.add(new CryptoCurrency("GBYTE", "Byte"));
        result.add(new CryptoCurrency("CLAM", "Clams"));
        result.add(new CryptoCurrency("CLOAK", "CloakCoin"));
        result.add(new CryptoCurrency("CMT", "Comet"));
        result.add(new CryptoCurrency("XCP", "Counterparty"));
        result.add(new CryptoCurrency("CRBIT", "Creditbit"));
        result.add(new CryptoCurrency("CRW", "Crown"));
        result.add(new CryptoCurrency("CBX", "Crypto Bullion"));
        result.add(new CryptoCurrency("DNET", "DarkNet"));
        result.add(new CryptoCurrency("DIBC", "DIBCOIN"));
        result.add(new CryptoCurrency("DASH", "Dash"));
        result.add(new CryptoCurrency("DCR", "Decred"));
        result.add(new CryptoCurrency("DGB", "Digibyte"));
        result.add(new CryptoCurrency("DRS", "Digital Rupees"));
        result.add(new CryptoCurrency("DGD", "DigixDAO Tokens", true));
        if (!baseCurrencyCode.equals("DOGE"))
            result.add(new CryptoCurrency("DOGE", "Dogecoin"));
        result.add(new CryptoCurrency("DMC", "DynamicCoin"));
        result.add(new CryptoCurrency("EMC", "Emercoin"));
        result.add(new CryptoCurrency("EURT", "EUR Tether"));
        result.add(new CryptoCurrency("ESP", "Espers"));
        result.add(new CryptoCurrency("ENT", "Eternity"));
        result.add(new CryptoCurrency("ETH", "Ether"));
        result.add(new CryptoCurrency("ETC", "Ether Classic"));
        result.add(new CryptoCurrency("ERC", "Europecoin"));
        result.add(new CryptoCurrency("EGC", "EverGreenCoin"));
        result.add(new CryptoCurrency("FCT", "Factom"));
        result.add(new CryptoCurrency("FAIR", "FairCoin"));
        result.add(new CryptoCurrency("FLO", "FlorinCoin"));
        result.add(new CryptoCurrency("GAME", "GameCredits"));
        result.add(new CryptoCurrency("GEMZ", "Gemz"));
        result.add(new CryptoCurrency("GRC", "Gridcoin"));
        result.add(new CryptoCurrency("GRS", "Groestlcoin"));
        result.add(new CryptoCurrency("NLG", "Gulden"));
        result.add(new CryptoCurrency("HODL", "HOdlcoin"));
        result.add(new CryptoCurrency("HNC", "HunCoin"));
        result.add(new CryptoCurrency("IOC", "I/O Coin"));
        result.add(new CryptoCurrency("IOP", "Fermat"));
        result.add(new CryptoCurrency("JNS", "Janus", true));
        result.add(new CryptoCurrency("JPYT", "JPY Tether"));
        result.add(new CryptoCurrency("JBS", "Jumbucks"));
        result.add(new CryptoCurrency("LBC", "LBRY Credits"));
        result.add(new CryptoCurrency("LTBC", "LTBcoin"));
        result.add(new CryptoCurrency("LSK", "Lisk"));
        if (!baseCurrencyCode.equals("LTC"))
            result.add(new CryptoCurrency("LTC", "Litecoin"));
        result.add(new CryptoCurrency("MAID", "MaidSafeCoin"));
        result.add(new CryptoCurrency("MKR", "Maker", true));
        result.add(new CryptoCurrency("MXT", "MarteXcoin"));
        result.add(new CryptoCurrency("MOIN", "Moin"));
        result.add(new CryptoCurrency("XMR", "Monero"));
        result.add(new CryptoCurrency("MT", "Mycelium Token", true));
        result.add(new CryptoCurrency("XMY", "Myriadcoin"));
        result.add(new CryptoCurrency("NAV", "Nav Coin"));
        result.add(new CryptoCurrency("XEM", "NEM"));
        result.add(new CryptoCurrency("NEVA", "Nevacoin"));
        result.add(new CryptoCurrency("NMC", "Namecoin"));
        result.add(new CryptoCurrency("NBT", "NuBits"));
        result.add(new CryptoCurrency("NSR", "NuShares"));
        result.add(new CryptoCurrency("NXT", "Nxt"));
        result.add(new CryptoCurrency("888", "OctoCoin"));
        result.add(new CryptoCurrency("OK", "OKCash"));
        result.add(new CryptoCurrency("OMNI", "Omni"));
        result.add(new CryptoCurrency("OPAL", "Opal"));
        result.add(new CryptoCurrency("PASC", "Pascal Coin", true));
        result.add(new CryptoCurrency("PPC", "Peercoin"));
        result.add(new CryptoCurrency("PEPECASH", "Pepe Cash"));
        result.add(new CryptoCurrency("PINK", "Pinkcoin"));
        result.add(new CryptoCurrency("PIVX", "PIVX"));
        result.add(new CryptoCurrency("XPTX", "PlatinumBar"));
        result.add(new CryptoCurrency("PLU", "Plutons", true));
        result.add(new CryptoCurrency("POST", "PostCoin"));
        result.add(new CryptoCurrency("POT", "PotCoin"));
        result.add(new CryptoCurrency("XPM", "Primecoin"));
        result.add(new CryptoCurrency("RADS", "Radium"));
        result.add(new CryptoCurrency("REALEST", "RealEst. Coin"));
        result.add(new CryptoCurrency("RDD", "ReddCoin"));
        result.add(new CryptoCurrency("XRP", "Ripple"));
        result.add(new CryptoCurrency("SFSC", "Safe FileSystem Coin"));
        result.add(new CryptoCurrency("SDC", "ShadowCash"));
        result.add(new CryptoCurrency("SHIFT", "Shift"));
        result.add(new CryptoCurrency("SC", "Siacoin"));
        result.add(new CryptoCurrency("SF", "Siafund"));
        result.add(new CryptoCurrency("SIB", "Sibcoin"));
        result.add(new CryptoCurrency("SMLY", "Smileycoin"));
        result.add(new CryptoCurrency("SLR", "SolarCoin"));
        result.add(new CryptoCurrency("STEEM", "STEEM"));
        result.add(new CryptoCurrency("STEEMUSD", "Steem Dollars", true));
        result.add(new CryptoCurrency("XLM", "Stellar Lumens"));
        result.add(new CryptoCurrency("SJCX", "StorjcoinX"));
        result.add(new CryptoCurrency("STRAT", "Stratis"));
        result.add(new CryptoCurrency("SWT", "Swarm City Token", true));
        result.add(new CryptoCurrency("SYNX", "Syndicate"));
        result.add(new CryptoCurrency("AMP", "Synereo", true));
        result.add(new CryptoCurrency("TRI", "Triangles"));
        result.add(new CryptoCurrency("USDT", "USD Tether"));
        result.add(new CryptoCurrency("UNO", "Unobtanium"));
        result.add(new CryptoCurrency("VCN", "VCoin"));
        result.add(new CryptoCurrency("VPN", "VPNCoin"));
        result.add(new CryptoCurrency("XVG", "Verge"));
        result.add(new CryptoCurrency("VRC", "VeriCoin"));
        result.add(new CryptoCurrency("WDC", "Worldcoin"));
        result.add(new CryptoCurrency("WAVES", "Waves"));
        result.add(new CryptoCurrency("XAUR", "Xaurum"));
        result.add(new CryptoCurrency("YACC", "YACCoin"));
        result.add(new CryptoCurrency("YBC", "YbCoin"));
        result.add(new CryptoCurrency("ZEC", "Zcash"));
        result.add(new CryptoCurrency("XZC", "Zcoin"));

        result.sort(TradeCurrency::compareTo);

        // Util for printing all altcoins for adding to FAQ page
        /* 
        StringBuilder sb = new StringBuilder();
        result.stream().forEach(e -> sb.append("<li>\"")
                .append(e.getCode())
                .append("\", \"")
                .append(e.getName())
                .append("\"</li>")
                .append("\n"));
        log.info(sb.toString());
        */
        return result;
    }

    public static List<CryptoCurrency> getMainCryptoCurrencies() {
        final List<CryptoCurrency> result = new ArrayList<>();
        //  result.add(new CryptoCurrency("BSQ", "Bisq Token"));
        if (!baseCurrencyCode.equals("BTC"))
            result.add(new CryptoCurrency("BTC", "Bitcoin"));
        result.add(new CryptoCurrency("DASH", "Dash"));
        result.add(new CryptoCurrency("DCR", "Decred"));
        if (!baseCurrencyCode.equals("DOGE"))
            result.add(new CryptoCurrency("DOGE", "Dogecoin"));
        result.add(new CryptoCurrency("ETH", "Ether"));
        result.add(new CryptoCurrency("ETC", "Ether Classic"));
        result.add(new CryptoCurrency("GRC", "Gridcoin"));
        if (!baseCurrencyCode.equals("LTC"))
            result.add(new CryptoCurrency("LTC", "Litecoin"));
        result.add(new CryptoCurrency("XMR", "Monero"));
        result.add(new CryptoCurrency("MT", "Mycelium Token", true));
        result.add(new CryptoCurrency("NMC", "Namecoin"));
        result.add(new CryptoCurrency("SC", "Siacoin"));
        result.add(new CryptoCurrency("SF", "Siafund"));
        result.add(new CryptoCurrency("UNO", "Unobtanium"));
        result.add(new CryptoCurrency("ZEC", "Zcash"));
        result.sort(TradeCurrency::compareTo);

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
        ArrayList<TradeCurrency> currencies = new ArrayList<>(Arrays.asList(
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
        currencies.sort(TradeCurrency::compareTo);
        return currencies;
    }

    public static boolean isFiatCurrency(String currencyCode) {
        try {
            return currencyCode != null && !currencyCode.isEmpty() && !isCryptoCurrency(currencyCode) && Currency.getInstance(currencyCode) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Optional<FiatCurrency> getFiatCurrency(String currencyCode) {
        return getAllSortedFiatCurrencies().stream().filter(e -> e.getCode().equals(currencyCode)).findAny();
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isCryptoCurrency(String currencyCode) {
        return getCryptoCurrency(currencyCode).isPresent();
    }

    public static Optional<CryptoCurrency> getCryptoCurrency(String currencyCode) {
        return getAllSortedCryptoCurrencies().stream().filter(e -> e.getCode().equals(currencyCode)).findAny();
    }

    public static Optional<TradeCurrency> getTradeCurrency(String currencyCode) {
        Optional<FiatCurrency> fiatCurrencyOptional = getFiatCurrency(currencyCode);
        if (isFiatCurrency(currencyCode) && fiatCurrencyOptional.isPresent()) {
            return Optional.of(fiatCurrencyOptional.get());
        } else {
            Optional<CryptoCurrency> cryptoCurrencyOptional = getCryptoCurrency(currencyCode);
            if (isCryptoCurrency(currencyCode) && cryptoCurrencyOptional.isPresent()) {
                return Optional.of(cryptoCurrencyOptional.get());
            } else {
                return Optional.empty();
            }
        }
    }


    public static FiatCurrency getCurrencyByCountryCode(String countryCode) {
        return new FiatCurrency(Currency.getInstance(new Locale(LanguageUtil.getDefaultLanguage(), countryCode)).getCurrencyCode());
    }


    public static String getNameByCode(String currencyCode) {
        if (isCryptoCurrency(currencyCode))
            return getCryptoCurrency(currencyCode).get().getName();
        else
            try {
                return Currency.getInstance(currencyCode).getDisplayName();
            } catch (Throwable t) {
                log.debug("No currency name available " + t.getMessage());
                return currencyCode;
            }
    }


    public static String getNameAndCode(String currencyCode) {
        return getNameByCode(currencyCode) + " (" + currencyCode + ")";
    }

    public static TradeCurrency getDefaultTradeCurrency() {
        return GlobalSettings.getDefaultTradeCurrency();
    }
}
