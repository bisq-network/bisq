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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CountryUtil {

    public static List<Country> getAllSepaEuroCountries() {
        List<Country> list = new ArrayList<>();
        String[] codes = {"AT", "BE", "CY", "DE", "EE", "FI", "FR", "GR", "IE",
                "IT", "LV", "LT", "LU", "MC", "MT", "NL", "PT", "SK", "SI", "ES"};
        populateCountryListByCodes(list, codes);
        list.sort((a, b) -> a.code.compareTo(b.code));

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
        list.sort((a, b) -> a.code.compareTo(b.code));
        return list;
    }

    public static List<Country> getAllSepaCountries() {
        List<Country> list = new ArrayList<>();
        list.addAll(getAllSepaEuroCountries());
        list.addAll(getAllSepaNonEuroCountries());
        return list;
    }

    public static Country getDefaultCountry() {
        final Locale locale = Preferences.getDefaultLocale();
        String regionCode = getRegionCode(locale.getCountry());
        final Region region = new Region(regionCode, getRegionName(regionCode));
        return new Country(locale.getCountry(), locale.getDisplayCountry(), region);
    }

    public static String getNameByCode(String countryCode) {
        return new Locale(LanguageUtil.getDefaultLanguage(), countryCode).getDisplayCountry();
    }

    public static String getCodesString(List<String> countryCodes) {
        return countryCodes.stream().collect(Collectors.joining(", "));
    }

    public static String getNamesByCodesString(List<String> countryCodes) {
        return getNamesByCodes(countryCodes).stream().collect(Collectors.joining(",\n"));
    }

    private static List<String> getNamesByCodes(List<String> countryCodes) {
        return countryCodes.stream().map(CountryUtil::getNameByCode).collect(Collectors.toList());
    }

    private static final String[] countryCodes = new String[]{"AE", "AL", "AR", "AT", "AU", "BA", "BE", "BG", "BH",
            "BO", "BR", "BY", "CA", "CH", "CL", "CN", "CO", "CR", "CS", "CU", "CY", "CZ", "DE", "DK", "DO", "DZ",
            "EC", "EE", "EG", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HR", "HU", "ID", "IE", "IL", "IN",
            "IQ", "IS", "IT", "JO", "JP", "KR", "KW", "LB", "LT", "LU", "LV", "LY", "MA", "ME", "MK", "MT", "MX",
            "MY", "NI", "NL", "NO", "NZ", "OM", "PA", "PE", "PH", "PL", "PR", "PT", "PY", "QA", "RO", "RS", "RU",
            "SA", "SD", "SE", "SG", "SI", "SK", "SV", "SY", "TH", "TN", "TR", "TW", "UA", "US", "UY", "VE", "VN",
            "YE", "ZA"};

    private static final List<String> countryCodeList = Arrays.asList(countryCodes);
    private static final String[] regionCodes = new String[]{"AS", "EU", "SA", "EU", "OC", "EU", "EU", "EU", "AS",
            "SA", "SA", "EU", "NA", "EU", "SA", "AS", "SA", "NA", "EU", "NA", "EU", "EU", "EU", "EU", "NA", "AF",
            "SA", "EU", "AF", "EU", "EU", "EU", "EU", "EU", "NA", "AS", "NA", "EU", "EU", "AS", "EU", "AS", "AS",
            "AS", "EU", "EU", "AS", "AS", "AS", "AS", "AS", "EU", "EU", "EU", "AF", "AF", "EU", "EU", "EU", "NA",
            "AS", "NA", "EU", "EU", "OC", "AS", "NA", "SA", "AS", "EU", "NA", "EU", "SA", "AS", "EU", "EU", "EU",
            "AS", "AF", "EU", "AS", "EU", "EU", "NA", "AS", "AS", "AF", "AS", "AS", "EU", "NA", "SA", "SA", "AS",
            "AS", "AF"};
    private static final List<String> regionCodeList = Arrays.asList(regionCodes);
    private static final String[][] regionCodeToName = new String[][]{
            {"NA", "North America"}, {"SA", "South America"}, {"AF", "Africa"}, {"EU", "Europe"}, {"AS", "Asia"},
            {"OC", "Oceania"}};

    private static String getRegionName(final String regionCode) {
        for (final String[] regionName : regionCodeToName) {
            if (regionName[0].equals(regionCode)) {
                return regionName[1];
            }
        }
        return regionCode;
    }

    private static String getRegionCode(String countryCode) {
        if (!countryCode.isEmpty() && countryCodeList.contains(countryCode)) {
            return regionCodeList.get(countryCodeList.indexOf(countryCode));
        } else {
            return "Undefined";
        }
    }

    public static String getDefaultCountryCode() {
        // might be set later in pref or config, so not use Preferences.getDefaultLocale() anywhere in the code
        return Preferences.getDefaultLocale().getCountry();
    }
}
