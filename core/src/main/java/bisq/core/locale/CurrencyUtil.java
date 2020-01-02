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

package bisq.core.locale;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.filter.FilterManager;

import bisq.asset.Asset;
import bisq.asset.AssetRegistry;
import bisq.asset.Coin;
import bisq.asset.Token;
import bisq.asset.coins.BSQ;

import bisq.common.app.DevEnv;

import com.google.common.base.Suppliers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class CurrencyUtil {

    public static void setup() {
        setBaseCurrencyCode(BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode());
    }

    private static final AssetRegistry assetRegistry = new AssetRegistry();

    private static String baseCurrencyCode = "BTC";

    private static Supplier<Map<String, FiatCurrency>> fiatCurrencyMapSupplier = Suppliers.memoize(
            CurrencyUtil::createFiatCurrencyMap)::get;
    private static Supplier<Map<String, CryptoCurrency>> cryptoCurrencyMapSupplier = Suppliers.memoize(
            CurrencyUtil::createCryptoCurrencyMap)::get;

    public static void setBaseCurrencyCode(String baseCurrencyCode) {
        CurrencyUtil.baseCurrencyCode = baseCurrencyCode;
    }

    public static Collection<FiatCurrency> getAllSortedFiatCurrencies() {
        return fiatCurrencyMapSupplier.get().values();
    }

    private static Map<String, FiatCurrency> createFiatCurrencyMap() {
        return CountryUtil.getAllCountries().stream()
                .map(country -> getCurrencyByCountryCode(country.code))
                .distinct()
                .sorted(TradeCurrency::compareTo)
                .collect(Collectors.toMap(TradeCurrency::getCode, Function.identity(), (x, y) -> x, LinkedHashMap::new));
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

        FiatCurrency defaultFiatCurrency =
                defaultTradeCurrency instanceof FiatCurrency ? (FiatCurrency) defaultTradeCurrency : null;
        if (defaultFiatCurrency != null && list.contains(defaultFiatCurrency)) {
            list.remove(defaultTradeCurrency);
            list.add(0, defaultFiatCurrency);
        }
        return list;
    }

    public static Collection<CryptoCurrency> getAllSortedCryptoCurrencies() {
        return cryptoCurrencyMapSupplier.get().values();
    }

    private static Map<String, CryptoCurrency> createCryptoCurrencyMap() {
        return getSortedAssetStream()
                .map(CurrencyUtil::assetToCryptoCurrency)
                .collect(Collectors.toMap(TradeCurrency::getCode, Function.identity(), (x, y) -> x, LinkedHashMap::new));
    }

    public static Stream<Asset> getSortedAssetStream() {
        return assetRegistry.stream()
                .filter(CurrencyUtil::assetIsNotBaseCurrency)
                .filter(asset -> isNotBsqOrBsqTradingActivated(asset, BisqEnvironment.getBaseCurrencyNetwork(), DevEnv.isDaoTradingActivated()))
                .filter(asset -> assetMatchesNetworkIfMainnet(asset, BisqEnvironment.getBaseCurrencyNetwork()))
                .sorted(Comparator.comparing(Asset::getName));
    }

    public static List<CryptoCurrency> getMainCryptoCurrencies() {
        final List<CryptoCurrency> result = new ArrayList<>();
        result.add(new CryptoCurrency("XRC", "Bitcoin Rhodium"));

        if (DevEnv.isDaoTradingActivated())
            result.add(new CryptoCurrency("BSQ", "BSQ"));

        result.add(new CryptoCurrency("BEAM", "Beam"));
        result.add(new CryptoCurrency("DASH", "Dash"));
        result.add(new CryptoCurrency("DCR", "Decred"));
        result.add(new CryptoCurrency("ETH", "Ether"));
        result.add(new CryptoCurrency("GRIN", "Grin"));
        result.add(new CryptoCurrency("LTC", "Litecoin"));
        result.add(new CryptoCurrency("XMR", "Monero"));
        result.add(new CryptoCurrency("NMC", "Namecoin"));
        result.add(new CryptoCurrency("SF", "Siafund"));
        result.add(new CryptoCurrency("ZEC", "Zcash"));
        result.sort(TradeCurrency::compareTo);

        return result;
    }

    public static List<CryptoCurrency> getRemovedCryptoCurrencies() {
        final List<CryptoCurrency> currencies = new ArrayList<>();
        currencies.add(new CryptoCurrency("BCH", "Bitcoin Cash"));
        currencies.add(new CryptoCurrency("BCHC", "Bitcoin Clashic"));
        currencies.add(new CryptoCurrency("ACH", "AchieveCoin"));
        currencies.add(new CryptoCurrency("SC", "Siacoin"));
        currencies.add(new CryptoCurrency("PPI", "PiedPiper Coin"));
        currencies.add(new CryptoCurrency("PEPECASH", "Pepe Cash"));
        currencies.add(new CryptoCurrency("GRC", "Gridcoin"));
        currencies.add(new CryptoCurrency("LTZ", "LitecoinZ"));
        currencies.add(new CryptoCurrency("ZOC", "01coin"));
        currencies.add(new CryptoCurrency("BURST", "Burstcoin"));
        currencies.add(new CryptoCurrency("STEEM", "Steem"));
        currencies.add(new CryptoCurrency("DAC", "DACash"));
        currencies.add(new CryptoCurrency("RDD", "ReddCoin"));
        return currencies;
    }

    public static List<TradeCurrency> getAllAdvancedCashCurrencies() {
        ArrayList<TradeCurrency> currencies = new ArrayList<>(Arrays.asList(
                new FiatCurrency("USD"),
                new FiatCurrency("EUR"),
                new FiatCurrency("GBP"),
                new FiatCurrency("RUB"),
                new FiatCurrency("UAH"),
                new FiatCurrency("KZT"),
                new FiatCurrency("BRL")
        ));
        currencies.sort(Comparator.comparing(TradeCurrency::getCode));
        return currencies;
    }

    public static List<TradeCurrency> getAllMoneyGramCurrencies() {
        ArrayList<TradeCurrency> currencies = new ArrayList<>(Arrays.asList(
                new FiatCurrency("AED"),
                new FiatCurrency("AUD"),
                new FiatCurrency("BND"),
                new FiatCurrency("CAD"),
                new FiatCurrency("CHF"),
                new FiatCurrency("CZK"),
                new FiatCurrency("DKK"),
                new FiatCurrency("EUR"),
                new FiatCurrency("FJD"),
                new FiatCurrency("GBP"),
                new FiatCurrency("HKD"),
                new FiatCurrency("HUF"),
                new FiatCurrency("IDR"),
                new FiatCurrency("ILS"),
                new FiatCurrency("INR"),
                new FiatCurrency("JPY"),
                new FiatCurrency("KRW"),
                new FiatCurrency("KWD"),
                new FiatCurrency("LKR"),
                new FiatCurrency("MAD"),
                new FiatCurrency("MGA"),
                new FiatCurrency("MXN"),
                new FiatCurrency("MYR"),
                new FiatCurrency("NOK"),
                new FiatCurrency("NZD"),
                new FiatCurrency("OMR"),
                new FiatCurrency("PEN"),
                new FiatCurrency("PGK"),
                new FiatCurrency("PHP"),
                new FiatCurrency("PKR"),
                new FiatCurrency("PLN"),
                new FiatCurrency("SAR"),
                new FiatCurrency("SBD"),
                new FiatCurrency("SCR"),
                new FiatCurrency("SEK"),
                new FiatCurrency("SGD"),
                new FiatCurrency("THB"),
                new FiatCurrency("TOP"),
                new FiatCurrency("TRY"),
                new FiatCurrency("TWD"),
                new FiatCurrency("USD"),
                new FiatCurrency("VND"),
                new FiatCurrency("VUV"),
                new FiatCurrency("WST"),
                new FiatCurrency("XOF"),
                new FiatCurrency("XPF"),
                new FiatCurrency("ZAR")
        ));

        currencies.sort(Comparator.comparing(TradeCurrency::getCode));
        return currencies;
    }

    // https://support.uphold.com/hc/en-us/articles/202473803-Supported-currencies
    public static List<TradeCurrency> getAllUpholdCurrencies() {
        ArrayList<TradeCurrency> currencies = new ArrayList<>(Arrays.asList(
                new FiatCurrency("USD"),
                new FiatCurrency("EUR"),
                new FiatCurrency("GBP"),
                new FiatCurrency("CNY"),
                new FiatCurrency("JPY"),
                new FiatCurrency("CHF"),
                new FiatCurrency("INR"),
                new FiatCurrency("MXN"),
                new FiatCurrency("AUD"),
                new FiatCurrency("CAD"),
                new FiatCurrency("HKD"),
                new FiatCurrency("NZD"),
                new FiatCurrency("SGD"),
                new FiatCurrency("KES"),
                new FiatCurrency("ILS"),
                new FiatCurrency("DKK"),
                new FiatCurrency("NOK"),
                new FiatCurrency("SEK"),
                new FiatCurrency("PLN"),
                new FiatCurrency("ARS"),
                new FiatCurrency("BRL"),
                new FiatCurrency("AED"),
                new FiatCurrency("PHP")
        ));

        currencies.sort(Comparator.comparing(TradeCurrency::getCode));
        return currencies;
    }

    //https://www.revolut.com/pa/faq#can-i-hold-multiple-currencies
    public static List<TradeCurrency> getAllRevolutCurrencies() {
        ArrayList<TradeCurrency> currencies = new ArrayList<>(Arrays.asList(
                new FiatCurrency("USD"),
                new FiatCurrency("GBP"),
                new FiatCurrency("EUR"),
                new FiatCurrency("PLN"),
                new FiatCurrency("CHF"),
                new FiatCurrency("DKK"),
                new FiatCurrency("NOK"),
                new FiatCurrency("SEK"),
                new FiatCurrency("RON"),
                new FiatCurrency("SGD"),
                new FiatCurrency("HKD"),
                new FiatCurrency("AUD"),
                new FiatCurrency("NZD"),
                new FiatCurrency("TRY"),
                new FiatCurrency("ILS"),
                new FiatCurrency("AED"),
                new FiatCurrency("CAD"),
                new FiatCurrency("HUF"),
                new FiatCurrency("INR"),
                new FiatCurrency("JPY"),
                new FiatCurrency("MAD"),
                new FiatCurrency("QAR"),
                new FiatCurrency("THB"),
                new FiatCurrency("ZAR")
        ));

        currencies.sort(Comparator.comparing(TradeCurrency::getCode));
        return currencies;
    }

    public static List<TradeCurrency> getMatureMarketCurrencies() {
        ArrayList<TradeCurrency> currencies = new ArrayList<>(Arrays.asList(
                new FiatCurrency("EUR"),
                new FiatCurrency("USD"),
                new FiatCurrency("GBP"),
                new FiatCurrency("CAD"),
                new FiatCurrency("AUD"),
                new FiatCurrency("BRL")
        ));
        currencies.sort(Comparator.comparing(TradeCurrency::getCode));
        return currencies;
    }

    public static boolean isFiatCurrency(String currencyCode) {
        try {
            return currencyCode != null
                    && !currencyCode.isEmpty()
                    && !isCryptoCurrency(currencyCode)
                    && Currency.getInstance(currencyCode) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Optional<FiatCurrency> getFiatCurrency(String currencyCode) {
        return Optional.ofNullable(fiatCurrencyMapSupplier.get().get(currencyCode));
    }

    /**
     * We return true if it is BTC or any of our currencies available in the assetRegistry.
     * For removed assets it would fail as they are not found but we don't want to conclude that they are fiat then.
     * As the caller might not deal with the case that a currency can be neither a cryptoCurrency nor Fiat if not found
     * we return true as well in case we have no fiat currency for the code.
     *
     * As we use a boolean result for isCryptoCurrency and isFiatCurrency we do not treat missing currencies correctly.
     * To throw an exception might be an option but that will require quite a lot of code change, so we don't do that
     * for the moment, but could be considered for the future. Another maybe better option is to introduce an enum which
     * contains 3 entries (CryptoCurrency, Fiat, Undefined).
     */
    public static boolean isCryptoCurrency(String currencyCode) {
        // Some tests call that method with null values. Should be fixed in the tests but to not break them return false.
        if (currencyCode == null)
            return false;

        // BTC is not part of our assetRegistry so treat it extra here. Other old base currencies (LTC, DOGE, DASH)
        // are not supported anymore so we can ignore that case.
        if (currencyCode.equals("BTC"))
            return true;

        // If we find the code in our assetRegistry we return true.
        // It might be that an asset was removed from the assetsRegistry, we deal with such cases below by checking if
        // it is a fiat currency
        if (getCryptoCurrency(currencyCode).isPresent())
            return true;

        // In case the code is from a removed asset we cross check if there exist a fiat currency with that code,
        // if we don't find a fiat currency we treat it as a crypto currency.
        if (!getFiatCurrency(currencyCode).isPresent())
            return true;

        // If we would have found a fiat currency we return false
        return false;
    }

    public static Optional<CryptoCurrency> getCryptoCurrency(String currencyCode) {
        return Optional.ofNullable(cryptoCurrencyMapSupplier.get().get(currencyCode));
    }

    public static Optional<TradeCurrency> getTradeCurrency(String currencyCode) {
        Optional<FiatCurrency> fiatCurrencyOptional = getFiatCurrency(currencyCode);
        if (fiatCurrencyOptional.isPresent() && isFiatCurrency(currencyCode))
            return Optional.of(fiatCurrencyOptional.get());

        Optional<CryptoCurrency> cryptoCurrencyOptional = getCryptoCurrency(currencyCode);
        if (cryptoCurrencyOptional.isPresent() && isCryptoCurrency(currencyCode))
            return Optional.of(cryptoCurrencyOptional.get());

        return Optional.empty();
    }

    public static FiatCurrency getCurrencyByCountryCode(String countryCode) {
        if (countryCode.equals("XK"))
            return new FiatCurrency("EUR");

        Currency currency = Currency.getInstance(new Locale(LanguageUtil.getDefaultLanguage(), countryCode));
        return new FiatCurrency(currency.getCurrencyCode());
    }


    public static String getNameByCode(String currencyCode) {
        if (isCryptoCurrency(currencyCode)) {
            // We might not find the name in case we have a call for a removed asset.
            // If BTC is the code (used in tests) we also want return Bitcoin as name.
            final Optional<CryptoCurrency> removedCryptoCurrency = getRemovedCryptoCurrencies().stream()
                    .filter(cryptoCurrency -> cryptoCurrency.getCode().equals(currencyCode))
                    .findAny();

            String btcOrRemovedAsset = "BTC".equals(currencyCode) ? "Bitcoin" :
                    removedCryptoCurrency.isPresent() ? removedCryptoCurrency.get().getName() : Res.get("shared.na");
            return getCryptoCurrency(currencyCode).map(TradeCurrency::getName).orElse(btcOrRemovedAsset);
        }
        try {
            return Currency.getInstance(currencyCode).getDisplayName();
        } catch (Throwable t) {
            log.debug("No currency name available {}", t.getMessage());
            return currencyCode;
        }
    }

    public static Optional<CryptoCurrency> findCryptoCurrencyByName(String currencyName) {
        return getAllSortedCryptoCurrencies().stream()
                .filter(e -> e.getName().equals(currencyName))
                .findAny();
    }

    public static String getNameAndCode(String currencyCode) {
        return getNameByCode(currencyCode) + " (" + currencyCode + ")";
    }

    public static TradeCurrency getDefaultTradeCurrency() {
        return GlobalSettings.getDefaultTradeCurrency();
    }

    private static boolean assetIsNotBaseCurrency(Asset asset) {
        return !assetMatchesCurrencyCode(asset, baseCurrencyCode);
    }

    // TODO We handle assets of other types (Token, ERC20) as matching the network which is not correct.
    // We should add support for network property in those tokens as well.
    public static boolean assetMatchesNetwork(Asset asset, BaseCurrencyNetwork baseCurrencyNetwork) {
        return !(asset instanceof Coin) ||
                ((Coin) asset).getNetwork().name().equals(baseCurrencyNetwork.getNetwork());
    }

    // We only check for coins not other types of assets (TODO network check should be supported for all assets)
    public static boolean assetMatchesNetworkIfMainnet(Asset asset, BaseCurrencyNetwork baseCurrencyNetwork) {
        return !(asset instanceof Coin) ||
                coinMatchesNetworkIfMainnet((Coin) asset, baseCurrencyNetwork);
    }

    // We want all coins available also in testnet or regtest for testing purpose
    public static boolean coinMatchesNetworkIfMainnet(Coin coin, BaseCurrencyNetwork baseCurrencyNetwork) {
        boolean matchesNetwork = assetMatchesNetwork(coin, baseCurrencyNetwork);
        return !baseCurrencyNetwork.isMainnet() || matchesNetwork;
    }

    private static CryptoCurrency assetToCryptoCurrency(Asset asset) {
        return new CryptoCurrency(asset.getTickerSymbol(), asset.getName(), asset instanceof Token);
    }

    private static boolean isNotBsqOrBsqTradingActivated(Asset asset, BaseCurrencyNetwork baseCurrencyNetwork, boolean daoTradingActivated) {
        return !(asset instanceof BSQ) ||
                daoTradingActivated && assetMatchesNetwork(asset, baseCurrencyNetwork);
    }

    public static boolean assetMatchesCurrencyCode(Asset asset, String currencyCode) {
        return currencyCode.equals(asset.getTickerSymbol());
    }

    public static Optional<Asset> findAsset(AssetRegistry assetRegistry, String currencyCode,
                                            BaseCurrencyNetwork baseCurrencyNetwork, boolean daoTradingActivated) {
        List<Asset> assets = assetRegistry.stream()
                .filter(asset -> assetMatchesCurrencyCode(asset, currencyCode)).collect(Collectors.toList());

        // If we don't have the ticker symbol we throw an exception
        if (!assets.stream().findFirst().isPresent())
            return Optional.empty();

        if (currencyCode.equals("BSQ") && baseCurrencyNetwork.isMainnet() && !daoTradingActivated)
            return Optional.empty();

        // We check for exact match with network, e.g. BTC$TESTNET
        Optional<Asset> optionalAssetMatchesNetwork = assets.stream()
                .filter(asset -> assetMatchesNetwork(asset, baseCurrencyNetwork))
                .findFirst();
        if (optionalAssetMatchesNetwork.isPresent())
            return optionalAssetMatchesNetwork;

        // In testnet or regtest we want to show all coins as well. Most coins have only Mainnet defined so we deliver
        // that if no exact match was found in previous step
        if (!baseCurrencyNetwork.isMainnet()) {
            Optional<Asset> optionalAsset = assets.stream().findFirst();
            checkArgument(optionalAsset.isPresent(), "optionalAsset must be present as we checked for " +
                    "not matching ticker symbols already above");
            return optionalAsset;
        }

        // If we are in mainnet we need have a mainnet asset defined.
        throw new IllegalArgumentException("We are on mainnet and we could not find an asset with network type mainnet");
    }

    public static Optional<Asset> findAsset(String tickerSymbol) {
        return assetRegistry.stream()
                .filter(asset -> asset.getTickerSymbol().equals(tickerSymbol))
                .findAny();
    }

    public static Optional<Asset> findAsset(String tickerSymbol, BaseCurrencyNetwork baseCurrencyNetwork) {
        return assetRegistry.stream()
                .filter(asset -> asset.getTickerSymbol().equals(tickerSymbol))
                .filter(asset -> assetMatchesNetwork(asset, baseCurrencyNetwork))
                .findAny();
    }

    // Excludes all assets which got removed by DAO voting
    public static List<CryptoCurrency> getActiveSortedCryptoCurrencies(AssetService assetService, FilterManager filterManager) {
        return getAllSortedCryptoCurrencies().stream()
                .filter(e -> e.getCode().equals("BSQ") || assetService.isActive(e.getCode()))
                .filter(e -> !filterManager.isCurrencyBanned(e.getCode()))
                .collect(Collectors.toList());
    }

    public static String getCurrencyPair(String currencyCode) {
        if (isFiatCurrency(currencyCode))
            return Res.getBaseCurrencyCode() + "/" + currencyCode;
        else
            return currencyCode + "/" + Res.getBaseCurrencyCode();
    }

    public static String getCounterCurrency(String currencyCode) {
        if (isFiatCurrency(currencyCode))
            return currencyCode;
        else
            return Res.getBaseCurrencyCode();
    }

    public static String getPriceWithCurrencyCode(String currencyCode) {
        return getPriceWithCurrencyCode(currencyCode, "shared.priceInCurForCur");
    }

    public static String getPriceWithCurrencyCode(String currencyCode, String translationKey) {
        if (isCryptoCurrency(currencyCode))
            return Res.get(translationKey, Res.getBaseCurrencyCode(), currencyCode);
        else
            return Res.get(translationKey, currencyCode, Res.getBaseCurrencyCode());
    }
}
