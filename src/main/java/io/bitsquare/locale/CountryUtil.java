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

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CountryUtil {
    private static final Logger log = LoggerFactory.getLogger(CountryUtil.class);

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

    public static List<Country> getAllEuroCountries() {
        List<Country> allEuroCountries = new ArrayList<>();
        String[] code = {"BE", "DE", "EE", "FI", "FR", "GR", "IE", "IT", "LV", "LU", "MT", "NL", "PT", "SK", "SI",
                "ES", "AT", "CY"};
        for (String aCode : code) {
            Locale locale = new Locale("", aCode, "");
            String regionCode = getRegionCode(locale.getCountry());
            final Region region = new Region(regionCode, getRegionName(regionCode));
            final Country country = new Country(locale.getCountry(), locale.getDisplayCountry(), region);
            allEuroCountries.add(country);
        }

        return allEuroCountries;
    }

    public static List<Country> getAllCountriesFor(Region selectedRegion) {
        return Lists.newArrayList(Collections2.filter(getAllCountries(), country ->
                selectedRegion != null && country != null && selectedRegion.equals(country.getRegion())));
    }

    private static List<Country> getAllCountries() {
        final List<Country> allCountries = new ArrayList<>();
        for (final Locale locale : getAllCountryLocales()) {
            String regionCode = getRegionCode(locale.getCountry());
            final Region region = new Region(regionCode, getRegionName(regionCode));
            final Country country = new Country(locale.getCountry(), locale.getDisplayCountry(), region);
            allCountries.add(country);
        }
        return allCountries;
    }

    public static Country getDefaultCountry() {
        final Locale locale = new Locale("", Locale.getDefault().getCountry());
        String regionCode = getRegionCode(locale.getCountry());
        final Region region = new Region(regionCode, getRegionName(regionCode));
        return new Country(locale.getCountry(), locale.getDisplayCountry(), region);
    }

    private static String getRegionName(final String regionCode) {
        for (final String[] regionName : regionCodeToName) {
            if (regionName[0].equals(regionCode)) {
                return regionName[1];
            }
        }
        return regionCode;
    }

    // We use getAvailableLocales as we depend on display names (would be a bit painful with translations if handled 
    // from a static list -or we find something ready made?). 
    private static List<Locale> getAllCountryLocales() {
        List<Locale> allLocales = Arrays.asList(Locale.getAvailableLocales());
        Set<Locale> allLocalesAsSet = allLocales.stream().filter(locale -> !"".equals(locale.getCountry()))
                .map(locale -> new Locale("", locale.getCountry(), ""))
                .collect(Collectors.toSet());

        allLocales = new ArrayList<>();
        allLocales.addAll(allLocalesAsSet);
        allLocales.sort((locale1, locale2) -> locale1.getDisplayCountry().compareTo(locale2.getDisplayCountry()));
        return allLocales;
    }

    private static String getRegionCode(String countryCode) {
        if (!countryCode.isEmpty() && countryCodeList.contains(countryCode)) {
            return regionCodeList.get(countryCodeList.indexOf(countryCode));
        }
        else {
            return "Undefined";
        }
    }

}
