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

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import io.bisq.common.GlobalSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CountryUtil {
    public static List<Country> getAllSepaEuroCountries() {
        List<Country> list = new ArrayList<>();
        String[] codes = {"AT", "BE", "CY", "DE", "EE", "FI", "FR", "GR", "IE",
                "IT", "LV", "LT", "LU", "MC", "MT", "NL", "PT", "SK", "SI", "ES"};
        populateCountryListByCodes(list, codes);
        list.sort((a, b) -> a.name.compareTo(b.name));

        return list;
    }

    private static void populateCountryListByCodes(List<Country> list, String[] codes) {
        for (String code : codes) {
            Locale locale = new Locale(LanguageUtil.getDefaultLanguage(), code, "");
            String regionCode = getRegionCode(locale.getCountry());
            final Region region = new Region(regionCode, getRegionName(regionCode));
            final Country country = new Country(locale.getCountry(), locale.getDisplayCountry(), region);
            list.add(country);
        }
    }

    public static boolean containsAllSepaEuroCountries(List<String> countryCodesToCompare) {
        countryCodesToCompare.sort(String::compareTo);
        List<String> countryCodesBase = getAllSepaEuroCountries().stream().map(c -> c.code).collect(Collectors.toList());
        return countryCodesToCompare.toString().equals(countryCodesBase.toString());
    }

    public static List<Country> getAllSepaNonEuroCountries() {
        List<Country> list = new ArrayList<>();
        String[] codes = {"BG", "HR", "CZ", "DK", "GB", "HU", "PL", "RO",
                "SE", "IS", "NO", "LI", "CH"};
        populateCountryListByCodes(list, codes);
        list.sort((a, b) -> a.name.compareTo(b.name));
        return list;
    }

    public static List<Country> getAllSepaCountries() {
        List<Country> list = new ArrayList<>();
        list.addAll(getAllSepaEuroCountries());
        list.addAll(getAllSepaNonEuroCountries());
        return list;
    }

    public static Country getDefaultCountry() {
        String regionCode = getRegionCode(getLocale().getCountry());
        final Region region = new Region(regionCode, getRegionName(regionCode));
        return new Country(getLocale().getCountry(), getLocale().getDisplayCountry(), region);
    }

    public static Optional<Country> findCountryByCode(String countryCode) {
        return getAllCountries().stream().filter(e -> e.code.equals(countryCode)).findAny();
    }

    public static String getNameByCode(String countryCode) {
        return new Locale(LanguageUtil.getDefaultLanguage(), countryCode).getDisplayCountry();
    }

    public static String getNameAndCode(String countryCode) {
        return getNameByCode(countryCode) + " (" + countryCode + ")";
    }

    public static String getCodesString(List<String> countryCodes) {
        return countryCodes.stream().collect(Collectors.joining(", "));
    }

    public static String getNamesByCodesString(List<String> countryCodes) {
        return getNamesByCodes(countryCodes).stream().collect(Collectors.joining(",\n"));
    }

    public static List<Region> getAllRegions() {
        final List<Region> allRegions = new ArrayList<>();

        String regionCode = "NA";
        Region region = new Region(regionCode, getRegionName(regionCode));
        allRegions.add(region);

        regionCode = "SA";
        region = new Region(regionCode, getRegionName(regionCode));
        allRegions.add(region);

        regionCode = "AF";
        region = new Region(regionCode, getRegionName(regionCode));
        allRegions.add(region);

        regionCode = "EU";
        region = new Region(regionCode, getRegionName(regionCode));
        allRegions.add(region);

        regionCode = "AS";
        region = new Region(regionCode, getRegionName(regionCode));
        allRegions.add(region);

        regionCode = "OC";
        region = new Region(regionCode, getRegionName(regionCode));
        allRegions.add(region);

        return allRegions;
    }

    public static List<Country> getAllCountriesForRegion(Region selectedRegion) {
        return Lists.newArrayList(Collections2.filter(getAllCountries(), country ->
                selectedRegion != null && country != null && selectedRegion.equals(country.region)));
    }

    public static List<Country> getAllCountries() {
        final Set<Country> allCountries = new HashSet<>();
        for (final Locale locale : getAllCountryLocales()) {
            String regionCode = getRegionCode(locale.getCountry());
            final Region region = new Region(regionCode, getRegionName(regionCode));
            final Country country = new Country(locale.getCountry(), locale.getDisplayCountry(), region);
            allCountries.add(country);
        }

        allCountries.add(new Country("GE", "Georgia", new Region("AS", getRegionName("AS"))));
        allCountries.add(new Country("BW", "Botswana", new Region("AF", getRegionName("AF"))));
        allCountries.add(new Country("IR", "Iran", new Region("AS", getRegionName("AS"))));

        final List<Country> allCountriesList = new ArrayList<>(allCountries);
        allCountriesList.sort((locale1, locale2) -> locale1.name.compareTo(locale2.name));
        return allCountriesList;
    }

    private static List<Locale> getAllCountryLocales() {
        List<Locale> allLocales = LocaleUtil.getAllLocales();

        // Filter duplicate locale entries
        Set<Locale> allLocalesAsSet = allLocales.stream().filter(locale -> !locale.getCountry().isEmpty())
                .collect(Collectors.toSet());

        List<Locale> allCountryLocales = new ArrayList<>();
        allCountryLocales.addAll(allLocalesAsSet);
        allCountryLocales.sort((locale1, locale2) -> locale1.getDisplayCountry().compareTo(locale2.getDisplayCountry()));
        return allCountryLocales;
    }

    private static List<String> getNamesByCodes(List<String> countryCodes) {
        return countryCodes.stream().map(CountryUtil::getNameByCode).collect(Collectors.toList());
    }

    private static String getRegionName(final String regionCode) {
        return regionCodeToNameMap.get(regionCode);
    }

    private static final Map<String, String> regionCodeToNameMap = new HashMap<>();
    private static final Map<String, String> regionByCountryCodeMap = new HashMap<>();

    static {
        regionCodeToNameMap.put("NA", "North America");
        regionCodeToNameMap.put("SA", "South America");
        regionCodeToNameMap.put("AF", "Africa");
        regionCodeToNameMap.put("EU", "Europe");
        regionCodeToNameMap.put("AS", "Asia");
        regionCodeToNameMap.put("OC", "Oceania");

        // source of countries: https://developers.braintreepayments.com/reference/general/countries/java
        regionByCountryCodeMap.put("AE", "AS"); // United Arab Emirates / Asia
        regionByCountryCodeMap.put("AL", "EU"); // Albania / Europe
        regionByCountryCodeMap.put("AR", "SA"); // Argentina / South America
        regionByCountryCodeMap.put("AT", "EU"); // Austria / Europe
        regionByCountryCodeMap.put("AU", "OC"); // Australia / Oceania
        regionByCountryCodeMap.put("BA", "EU"); // Bosnia and Herzegovina / Europe
        regionByCountryCodeMap.put("BE", "EU"); // Belgium / Europe
        regionByCountryCodeMap.put("BG", "EU"); // Bulgaria / Europe
        regionByCountryCodeMap.put("BH", "AS"); // Bahrain / Asia
        regionByCountryCodeMap.put("BO", "SA"); // Bolivia / South America
        regionByCountryCodeMap.put("BR", "SA"); // Brazil / South America
        regionByCountryCodeMap.put("BY", "EU"); // Belarus / Europe
        regionByCountryCodeMap.put("CA", "NA"); // Canada / North America
        regionByCountryCodeMap.put("CH", "EU"); // Switzerland / Europe
        regionByCountryCodeMap.put("CL", "SA"); // Chile / South America
        regionByCountryCodeMap.put("CN", "AS"); // China / Asia
        regionByCountryCodeMap.put("CO", "SA"); // Colombia / South America
        regionByCountryCodeMap.put("CR", "NA"); // Costa Rica / North America
        regionByCountryCodeMap.put("CS", "EU"); // Serbia and Montenegro / Europe
        regionByCountryCodeMap.put("CU", "NA"); // Cuba / North America
        regionByCountryCodeMap.put("CY", "EU"); // Cyprus / Europe
        regionByCountryCodeMap.put("CZ", "EU"); // Czech Republic / Europe
        regionByCountryCodeMap.put("DE", "EU"); // Germany / Europe
        regionByCountryCodeMap.put("DK", "EU"); // Denmark / Europe
        regionByCountryCodeMap.put("DO", "NA"); // Dominican Republic / North America
        regionByCountryCodeMap.put("EC", "SA"); // Ecuador / South America
        regionByCountryCodeMap.put("EE", "EU"); // Estonia / Europe
        regionByCountryCodeMap.put("ES", "EU"); // Spain / Europe
        regionByCountryCodeMap.put("FI", "EU"); // Finland / Europe
        regionByCountryCodeMap.put("FR", "EU"); // France / Europe
        regionByCountryCodeMap.put("GE", "AS"); // Georgia / Asia
        regionByCountryCodeMap.put("GB", "EU"); // United Kingdom / Europe
        regionByCountryCodeMap.put("GR", "EU"); // Greece / Europe
        regionByCountryCodeMap.put("GT", "NA"); // Guatemala / North America
        regionByCountryCodeMap.put("HK", "AS"); // Hong Kong / Asia
        regionByCountryCodeMap.put("HN", "NA"); // Honduras / North America
        regionByCountryCodeMap.put("HR", "EU"); // Croatia / Europe
        regionByCountryCodeMap.put("HU", "EU"); // Hungary / Europe
        regionByCountryCodeMap.put("ID", "AS"); // Indonesia / Asia
        regionByCountryCodeMap.put("IE", "EU"); // Ireland / Europe
        regionByCountryCodeMap.put("IL", "AS"); // Israel / Asia
        regionByCountryCodeMap.put("IN", "AS"); // India / Asia
        regionByCountryCodeMap.put("IQ", "AS"); // Iraq / Asia
        regionByCountryCodeMap.put("IR", "AS"); // Iran / Asia
        regionByCountryCodeMap.put("IS", "EU"); // Iceland / Europe
        regionByCountryCodeMap.put("IT", "EU"); // Italy / Europe
        regionByCountryCodeMap.put("JO", "AS"); // Jordan / Asia
        regionByCountryCodeMap.put("JP", "AS"); // Japan / Asia
        regionByCountryCodeMap.put("KH", "AS"); // Cambodia / Asia
        regionByCountryCodeMap.put("KR", "AS"); // South Korea / Asia
        regionByCountryCodeMap.put("KW", "AS"); // Kuwait / Asia
        regionByCountryCodeMap.put("KZ", "AS"); // Kazakhstan / Asia
        regionByCountryCodeMap.put("LB", "AS"); // Lebanon / Asia
        regionByCountryCodeMap.put("LT", "EU"); // Lithuania / Europe
        regionByCountryCodeMap.put("LU", "EU"); // Luxembourg / Europe
        regionByCountryCodeMap.put("LV", "EU"); // Latvia / Europe
        regionByCountryCodeMap.put("MD", "EU"); // Moldova / Europe
        regionByCountryCodeMap.put("ME", "EU"); // Montenegro / Europe
        regionByCountryCodeMap.put("MK", "EU"); // Macedonia / Europe
        regionByCountryCodeMap.put("MT", "EU"); // Malta / Europe
        regionByCountryCodeMap.put("MX", "NA"); // Mexico / North America
        regionByCountryCodeMap.put("MY", "AS"); // Malaysia / Asia
        regionByCountryCodeMap.put("NI", "NA"); // Nicaragua / North America
        regionByCountryCodeMap.put("NL", "EU"); // Netherlands / Europe
        regionByCountryCodeMap.put("NO", "EU"); // Norway / Europe
        regionByCountryCodeMap.put("NZ", "OC"); // New Zealand / Oceania
        regionByCountryCodeMap.put("OM", "AS"); // Oman / Asia
        regionByCountryCodeMap.put("PA", "NA"); // Panama / North America
        regionByCountryCodeMap.put("PE", "SA"); // Peru / South America
        regionByCountryCodeMap.put("PH", "AS"); // Philippines / Asia
        regionByCountryCodeMap.put("PL", "EU"); // Poland / Europe
        regionByCountryCodeMap.put("PR", "NA"); // Puerto Rico / North America
        regionByCountryCodeMap.put("PT", "EU"); // Portugal / Europe
        regionByCountryCodeMap.put("PY", "SA"); // Paraguay / South America
        regionByCountryCodeMap.put("QA", "AS"); // Qatar / Asia
        regionByCountryCodeMap.put("RO", "EU"); // Romania / Europe
        regionByCountryCodeMap.put("RS", "EU"); // Serbia / Europe
        regionByCountryCodeMap.put("RU", "EU"); // Russia / Europe
        regionByCountryCodeMap.put("SA", "AS"); // Saudi Arabia / Asia
        regionByCountryCodeMap.put("SE", "EU"); // Sweden / Europe
        regionByCountryCodeMap.put("SG", "AS"); // Singapore / Asia
        regionByCountryCodeMap.put("SI", "EU"); // Slovenia / Europe
        regionByCountryCodeMap.put("SK", "EU"); // Slovakia / Europe
        regionByCountryCodeMap.put("SV", "NA"); // El Salvador / North America
        regionByCountryCodeMap.put("SY", "AS"); // Syria / Asia
        regionByCountryCodeMap.put("TH", "AS"); // Thailand / Asia
        regionByCountryCodeMap.put("TR", "AS"); // Turkey / Asia
        regionByCountryCodeMap.put("TW", "AS"); // Taiwan / Asia
        regionByCountryCodeMap.put("UA", "EU"); // Ukraine / Europe
        regionByCountryCodeMap.put("US", "NA"); // United States / North America
        regionByCountryCodeMap.put("UY", "SA"); // Uruguay / South America
        regionByCountryCodeMap.put("VE", "SA"); // Venezuela / South America
        regionByCountryCodeMap.put("VN", "AS"); // Vietnam / Asia
        regionByCountryCodeMap.put("YE", "AS"); // Yemen / Asia

        // all african
        regionByCountryCodeMap.put("DZ", "AF"); // Algeria / Africa
        regionByCountryCodeMap.put("AO", "AF"); // Angola / Africa
        regionByCountryCodeMap.put("SH", "AF"); // Ascension / Africa
        regionByCountryCodeMap.put("BJ", "AF"); // Benin / Africa
        regionByCountryCodeMap.put("BW", "AF"); // Botswana / Africa
        regionByCountryCodeMap.put("BF", "AF"); // Burkina Faso / Africa
        regionByCountryCodeMap.put("BI", "AF"); // Burundi / Africa
        regionByCountryCodeMap.put("CM", "AF"); // Cameroon / Africa
        regionByCountryCodeMap.put("CV", "AF"); // Cape Verde Islands / Africa
        regionByCountryCodeMap.put("CF", "AF"); // Central African Republic / Africa
        regionByCountryCodeMap.put("TD", "AF"); // Chad Republic / Africa
        regionByCountryCodeMap.put("KM", "AF"); // Comoros / Africa
        regionByCountryCodeMap.put("CG", "AF"); // Congo / Africa
        regionByCountryCodeMap.put("CD", "AF"); // Dem. Republic of the Congo / Africa
        regionByCountryCodeMap.put("DJ", "AF"); // Djibouti / Africa
        regionByCountryCodeMap.put("EG", "AF"); // Egypt / Africa
        regionByCountryCodeMap.put("GQ", "AF"); // Equatorial Guinea / Africa
        regionByCountryCodeMap.put("ER", "AF"); // Eritrea / Africa
        regionByCountryCodeMap.put("ET", "AF"); // Ethiopia / Africa
        regionByCountryCodeMap.put("GA", "AF"); // Gabon Republic / Africa
        regionByCountryCodeMap.put("GM", "AF"); // Gambia / Africa
        regionByCountryCodeMap.put("GH", "AF"); // Ghana / Africa
        regionByCountryCodeMap.put("GN", "AF"); // Guinea / Africa
        regionByCountryCodeMap.put("GW", "AF"); // Guinea-Bissau / Africa
        regionByCountryCodeMap.put("CI", "AF"); // Ivory Coast / Africa
        regionByCountryCodeMap.put("KE", "AF"); // Kenya / Africa
        regionByCountryCodeMap.put("LS", "AF"); // Lesotho / Africa
        regionByCountryCodeMap.put("LR", "AF"); // Liberia / Africa
        regionByCountryCodeMap.put("LY", "AF"); // Libya / Africa
        regionByCountryCodeMap.put("MG", "AF"); // Madagascar / Africa
        regionByCountryCodeMap.put("MW", "AF"); // Malawi / Africa
        regionByCountryCodeMap.put("ML", "AF"); // Mali Republic / Africa
        regionByCountryCodeMap.put("MR", "AF"); // Mauritania / Africa
        regionByCountryCodeMap.put("MU", "AF"); // Mauritius / Africa
        regionByCountryCodeMap.put("YT", "AF"); // Mayotte Island / Africa
        regionByCountryCodeMap.put("MA", "AF"); // Morocco / Africa
        regionByCountryCodeMap.put("MZ", "AF"); // Mozambique / Africa
        regionByCountryCodeMap.put("NA", "AF"); // Namibia / Africa
        regionByCountryCodeMap.put("NE", "AF"); // Niger Republic / Africa
        regionByCountryCodeMap.put("NG", "AF"); // Nigeria / Africa
        regionByCountryCodeMap.put("ST", "AF"); // Principe / Africa
        regionByCountryCodeMap.put("RE", "AF"); // Reunion Island / Africa
        regionByCountryCodeMap.put("RW", "AF"); // Rwanda / Africa
        regionByCountryCodeMap.put("ST", "AF"); // Sao Tome / Africa
        regionByCountryCodeMap.put("SN", "AF"); // Senegal Republic / Africa
        regionByCountryCodeMap.put("SC", "AF"); // Seychelles / Africa
        regionByCountryCodeMap.put("SL", "AF"); // Sierra Leone / Africa
        regionByCountryCodeMap.put("SO", "AF"); // Somalia Republic / Africa
        regionByCountryCodeMap.put("ZA", "AF"); // South Africa / Africa
        regionByCountryCodeMap.put("SS", "AF"); // South Sudan / Africa
        regionByCountryCodeMap.put("SH", "AF"); // St. Helena / Africa
        regionByCountryCodeMap.put("SD", "AF"); // Sudan / Africa
        regionByCountryCodeMap.put("SZ", "AF"); // Swaziland / Africa
        regionByCountryCodeMap.put("TZ", "AF"); // Tanzania / Africa
        regionByCountryCodeMap.put("TG", "AF"); // Togo / Africa
        regionByCountryCodeMap.put("TN", "AF"); // Tunisia / Africa
        regionByCountryCodeMap.put("UG", "AF"); // Uganda / Africa
        regionByCountryCodeMap.put("CD", "AF"); // Zaire / Africa
        regionByCountryCodeMap.put("ZM", "AF"); // Zambia / Africa
        regionByCountryCodeMap.put("TZ", "AF"); // Zanzibar / Africa
        regionByCountryCodeMap.put("ZW", "AF"); // Zimbabwe / Africa
    }

    public static String getRegionCode(String countryCode) {
        if (regionByCountryCodeMap.containsKey(countryCode))
            return regionByCountryCodeMap.get(countryCode);
        else
            return "Undefined";
    }

    public static String getDefaultCountryCode() {
        // might be set later in pref or config, so not use Preferences.getDefaultLocale() anywhere in the code
        return getLocale().getCountry();
    }

    private static Locale getLocale() {
        return GlobalSettings.getLocale();
    }
}
