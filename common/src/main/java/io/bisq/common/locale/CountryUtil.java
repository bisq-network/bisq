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

    public static List<Country> getAllSepaInstantEuroCountries() {
        return getAllSepaEuroCountries();
    }

    private static void populateCountryListByCodes(List<Country> list, String[] codes) {
        for (String code : codes) {
            Locale locale = new Locale(LanguageUtil.getDefaultLanguage(), code, "");
            final String countryCode = locale.getCountry();
            String regionCode = getRegionCode(countryCode);
            final Region region = new Region(regionCode, getRegionName(regionCode));
            Country country = new Country(countryCode, locale.getDisplayCountry(), region);
            if (countryCode.equals("XK"))
                country = new Country(countryCode, getNameByCode(countryCode), region);
            list.add(country);
        }
    }

    public static boolean containsAllSepaEuroCountries(List<String> countryCodesToCompare) {
        countryCodesToCompare.sort(String::compareTo);
        List<String> countryCodesBase = getAllSepaEuroCountries().stream().map(c -> c.code).collect(Collectors.toList());
        return countryCodesToCompare.toString().equals(countryCodesBase.toString());
    }

    public static boolean containsAllSepaInstantEuroCountries(List<String> countryCodesToCompare) {
        return containsAllSepaEuroCountries(countryCodesToCompare);
    }

    public static List<Country> getAllSepaNonEuroCountries() {
        List<Country> list = new ArrayList<>();
        String[] codes = {"BG", "HR", "CZ", "DK", "GB", "HU", "PL", "RO",
                "SE", "IS", "NO", "LI", "CH"};
        populateCountryListByCodes(list, codes);
        list.sort((a, b) -> a.name.compareTo(b.name));
        return list;
    }

    public static List<Country> getAllSepaInstantNonEuroCountries() {
        return getAllSepaNonEuroCountries();
    }

    public static List<Country> getAllSepaCountries() {
        List<Country> list = new ArrayList<>();
        list.addAll(getAllSepaEuroCountries());
        list.addAll(getAllSepaNonEuroCountries());
        return list;
    }

    public static List<Country> getAllSepaInstantCountries() {
        // TODO find reliable source for list
        // //Austria, Estonia, Germany, Italy, Latvia, Lithuania, the Netherlands and Spain.

       /* List<Country> list = new ArrayList<>();
        String[] codes = {"AT", "DE", "EE",
                "IT", "LV", "LT", "NL", "ES"};
        populateCountryListByCodes(list, codes);
        list.sort((a, b) -> a.name.compareTo(b.name));*/
        return getAllSepaCountries();
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
        if (countryCode.equals("XK"))
            return "Republic of Kosovo";
        else
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

        String regionCode = "AM";
        Region region = new Region(regionCode, getRegionName(regionCode));
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
            Country country = new Country(locale.getCountry(), locale.getDisplayCountry(), region);
            if (locale.getCountry().equals("XK"))
                country = new Country(locale.getCountry(), "Republic of Kosovo", region);
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
    // Key is: ISO 3166 code, value is region code as defined in regionCodeToNameMap
    private static final Map<String, String> regionByCountryCodeMap = new HashMap<>();

    static {
        regionCodeToNameMap.put("AM", "Americas");
        regionCodeToNameMap.put("AF", "Africa");
        regionCodeToNameMap.put("EU", "Europe");
        regionCodeToNameMap.put("AS", "Asia");
        regionCodeToNameMap.put("OC", "Oceania");

        // Data extracted from https://restcountries.eu/rest/v2/all?fields=name;region;subregion;alpha2Code;languages
        regionByCountryCodeMap.put("AF", "AS"); // name=Afghanistan / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("AX", "EU"); // name=Åland Islands / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("AL", "EU"); // name=Albania / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("DZ", "AF"); // name=Algeria / region=Africa / subregion=Northern Africa
        regionByCountryCodeMap.put("AS", "OC"); // name=American Samoa / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("AD", "EU"); // name=Andorra / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("AO", "AF"); // name=Angola / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("AI", "AM"); // name=Anguilla / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("AG", "AM"); // name=Antigua and Barbuda / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("AR", "AM"); // name=Argentina / region=Americas / subregion=South America
        regionByCountryCodeMap.put("AM", "AS"); // name=Armenia / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("AW", "AM"); // name=Aruba / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("AU", "OC"); // name=Australia / region=Oceania / subregion=Australia and New Zealand
        regionByCountryCodeMap.put("AT", "EU"); // name=Austria / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("AZ", "AS"); // name=Azerbaijan / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("BS", "AM"); // name=Bahamas / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("BH", "AS"); // name=Bahrain / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("BD", "AS"); // name=Bangladesh / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("BB", "AM"); // name=Barbados / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("BY", "EU"); // name=Belarus / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("BE", "EU"); // name=Belgium / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("BZ", "AM"); // name=Belize / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("BJ", "AF"); // name=Benin / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("BM", "AM"); // name=Bermuda / region=Americas / subregion=Northern America
        regionByCountryCodeMap.put("BT", "AS"); // name=Bhutan / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("BO", "AM"); // name=Bolivia (Plurinational State of) / region=Americas / subregion=South America
        regionByCountryCodeMap.put("BQ", "AM"); // name=Bonaire, Sint Eustatius and Saba / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("BA", "EU"); // name=Bosnia and Herzegovina / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("BW", "AF"); // name=Botswana / region=Africa / subregion=Southern Africa
        regionByCountryCodeMap.put("BR", "AM"); // name=Brazil / region=Americas / subregion=South America
        regionByCountryCodeMap.put("IO", "AF"); // name=British Indian Ocean Territory / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("UM", "AM"); // name=United States Minor Outlying Islands / region=Americas / subregion=Northern America
        regionByCountryCodeMap.put("VG", "AM"); // name=Virgin Islands (British) / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("VI", "AM"); // name=Virgin Islands (U.S.) / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("BN", "AS"); // name=Brunei Darussalam / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("BG", "EU"); // name=Bulgaria / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("BF", "AF"); // name=Burkina Faso / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("BI", "AF"); // name=Burundi / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("KH", "AS"); // name=Cambodia / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("CM", "AF"); // name=Cameroon / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("CA", "AM"); // name=Canada / region=Americas / subregion=Northern America
        regionByCountryCodeMap.put("CV", "AF"); // name=Cabo Verde / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("KY", "AM"); // name=Cayman Islands / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("CF", "AF"); // name=Central African Republic / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("TD", "AF"); // name=Chad / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("CL", "AM"); // name=Chile / region=Americas / subregion=South America
        regionByCountryCodeMap.put("CN", "AS"); // name=China / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("CX", "OC"); // name=Christmas Island / region=Oceania / subregion=Australia and New Zealand
        regionByCountryCodeMap.put("CC", "OC"); // name=Cocos (Keeling) Islands / region=Oceania / subregion=Australia and New Zealand
        regionByCountryCodeMap.put("CO", "AM"); // name=Colombia / region=Americas / subregion=South America
        regionByCountryCodeMap.put("KM", "AF"); // name=Comoros / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("CG", "AF"); // name=Congo / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("CD", "AF"); // name=Congo (Democratic Republic of the) / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("CK", "OC"); // name=Cook Islands / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("CR", "AM"); // name=Costa Rica / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("HR", "EU"); // name=Croatia / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("CU", "AM"); // name=Cuba / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("CW", "AM"); // name=Curaçao / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("CY", "EU"); // name=Cyprus / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("CZ", "EU"); // name=Czech Republic / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("DK", "EU"); // name=Denmark / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("DJ", "AF"); // name=Djibouti / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("DM", "AM"); // name=Dominica / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("DO", "AM"); // name=Dominican Republic / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("EC", "AM"); // name=Ecuador / region=Americas / subregion=South America
        regionByCountryCodeMap.put("EG", "AF"); // name=Egypt / region=Africa / subregion=Northern Africa
        regionByCountryCodeMap.put("SV", "AM"); // name=El Salvador / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("GQ", "AF"); // name=Equatorial Guinea / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("ER", "AF"); // name=Eritrea / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("EE", "EU"); // name=Estonia / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("ET", "AF"); // name=Ethiopia / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("FK", "AM"); // name=Falkland Islands (Malvinas) / region=Americas / subregion=South America
        regionByCountryCodeMap.put("FO", "EU"); // name=Faroe Islands / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("FJ", "OC"); // name=Fiji / region=Oceania / subregion=Melanesia
        regionByCountryCodeMap.put("FI", "EU"); // name=Finland / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("FR", "EU"); // name=France / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("GF", "AM"); // name=French Guiana / region=Americas / subregion=South America
        regionByCountryCodeMap.put("PF", "OC"); // name=French Polynesia / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("TF", "AF"); // name=French Southern Territories / region=Africa / subregion=Southern Africa
        regionByCountryCodeMap.put("GA", "AF"); // name=Gabon / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("GM", "AF"); // name=Gambia / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("GE", "AS"); // name=Georgia / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("DE", "EU"); // name=Germany / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("GH", "AF"); // name=Ghana / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("GI", "EU"); // name=Gibraltar / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("GR", "EU"); // name=Greece / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("GL", "AM"); // name=Greenland / region=Americas / subregion=Northern America
        regionByCountryCodeMap.put("GD", "AM"); // name=Grenada / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("GP", "AM"); // name=Guadeloupe / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("GU", "OC"); // name=Guam / region=Oceania / subregion=Micronesia
        regionByCountryCodeMap.put("GT", "AM"); // name=Guatemala / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("GG", "EU"); // name=Guernsey / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("GN", "AF"); // name=Guinea / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("GW", "AF"); // name=Guinea-Bissau / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("GY", "AM"); // name=Guyana / region=Americas / subregion=South America
        regionByCountryCodeMap.put("HT", "AM"); // name=Haiti / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("VA", "EU"); // name=Holy See / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("HN", "AM"); // name=Honduras / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("HK", "AS"); // name=Hong Kong / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("HU", "EU"); // name=Hungary / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("IS", "EU"); // name=Iceland / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("IN", "AS"); // name=India / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("ID", "AS"); // name=Indonesia / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("CI", "AF"); // name=Côte d'Ivoire / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("IR", "AS"); // name=Iran (Islamic Republic of) / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("IQ", "AS"); // name=Iraq / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("IE", "EU"); // name=Ireland / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("IM", "EU"); // name=Isle of Man / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("IL", "AS"); // name=Israel / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("IT", "EU"); // name=Italy / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("JM", "AM"); // name=Jamaica / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("JP", "AS"); // name=Japan / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("JE", "EU"); // name=Jersey / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("JO", "AS"); // name=Jordan / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("KZ", "AS"); // name=Kazakhstan / region=Asia / subregion=Central Asia
        regionByCountryCodeMap.put("KE", "AF"); // name=Kenya / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("KI", "OC"); // name=Kiribati / region=Oceania / subregion=Micronesia
        regionByCountryCodeMap.put("KW", "AS"); // name=Kuwait / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("KG", "AS"); // name=Kyrgyzstan / region=Asia / subregion=Central Asia
        regionByCountryCodeMap.put("LA", "AS"); // name=Lao People's Democratic Republic / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("LV", "EU"); // name=Latvia / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("LB", "AS"); // name=Lebanon / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("LS", "AF"); // name=Lesotho / region=Africa / subregion=Southern Africa
        regionByCountryCodeMap.put("LR", "AF"); // name=Liberia / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("LY", "AF"); // name=Libya / region=Africa / subregion=Northern Africa
        regionByCountryCodeMap.put("LI", "EU"); // name=Liechtenstein / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("LT", "EU"); // name=Lithuania / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("LU", "EU"); // name=Luxembourg / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("MO", "AS"); // name=Macao / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("MK", "EU"); // name=Macedonia (the former Yugoslav Republic of) / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("MG", "AF"); // name=Madagascar / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("MW", "AF"); // name=Malawi / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("MY", "AS"); // name=Malaysia / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("MV", "AS"); // name=Maldives / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("ML", "AF"); // name=Mali / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("MT", "EU"); // name=Malta / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("MH", "OC"); // name=Marshall Islands / region=Oceania / subregion=Micronesia
        regionByCountryCodeMap.put("MQ", "AM"); // name=Martinique / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("MR", "AF"); // name=Mauritania / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("MU", "AF"); // name=Mauritius / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("YT", "AF"); // name=Mayotte / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("MX", "AM"); // name=Mexico / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("FM", "OC"); // name=Micronesia (Federated States of) / region=Oceania / subregion=Micronesia
        regionByCountryCodeMap.put("MD", "EU"); // name=Moldova (Republic of) / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("MC", "EU"); // name=Monaco / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("MN", "AS"); // name=Mongolia / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("ME", "EU"); // name=Montenegro / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("MS", "AM"); // name=Montserrat / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("MA", "AF"); // name=Morocco / region=Africa / subregion=Northern Africa
        regionByCountryCodeMap.put("MZ", "AF"); // name=Mozambique / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("MM", "AS"); // name=Myanmar / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("NA", "AF"); // name=Namibia / region=Africa / subregion=Southern Africa
        regionByCountryCodeMap.put("NR", "OC"); // name=Nauru / region=Oceania / subregion=Micronesia
        regionByCountryCodeMap.put("NP", "AS"); // name=Nepal / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("NL", "EU"); // name=Netherlands / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("NC", "OC"); // name=New Caledonia / region=Oceania / subregion=Melanesia
        regionByCountryCodeMap.put("NZ", "OC"); // name=New Zealand / region=Oceania / subregion=Australia and New Zealand
        regionByCountryCodeMap.put("NI", "AM"); // name=Nicaragua / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("NE", "AF"); // name=Niger / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("NG", "AF"); // name=Nigeria / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("NU", "OC"); // name=Niue / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("NF", "OC"); // name=Norfolk Island / region=Oceania / subregion=Australia and New Zealand
        regionByCountryCodeMap.put("KP", "AS"); // name=Korea (Democratic People's Republic of) / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("MP", "OC"); // name=Northern Mariana Islands / region=Oceania / subregion=Micronesia
        regionByCountryCodeMap.put("NO", "EU"); // name=Norway / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("OM", "AS"); // name=Oman / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("PK", "AS"); // name=Pakistan / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("PW", "OC"); // name=Palau / region=Oceania / subregion=Micronesia
        regionByCountryCodeMap.put("PS", "AS"); // name=Palestine, State of / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("PA", "AM"); // name=Panama / region=Americas / subregion=Central America
        regionByCountryCodeMap.put("PG", "OC"); // name=Papua New Guinea / region=Oceania / subregion=Melanesia
        regionByCountryCodeMap.put("PY", "AM"); // name=Paraguay / region=Americas / subregion=South America
        regionByCountryCodeMap.put("PE", "AM"); // name=Peru / region=Americas / subregion=South America
        regionByCountryCodeMap.put("PH", "AS"); // name=Philippines / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("PN", "OC"); // name=Pitcairn / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("PL", "EU"); // name=Poland / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("PT", "EU"); // name=Portugal / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("PR", "AM"); // name=Puerto Rico / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("QA", "AS"); // name=Qatar / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("XK", "EU"); // name=Republic of Kosovo / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("RE", "AF"); // name=Réunion / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("RO", "EU"); // name=Romania / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("RU", "EU"); // name=Russian Federation / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("RW", "AF"); // name=Rwanda / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("BL", "AM"); // name=Saint Barthélemy / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("SH", "AF"); // name=Saint Helena, Ascension and Tristan da Cunha / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("KN", "AM"); // name=Saint Kitts and Nevis / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("LC", "AM"); // name=Saint Lucia / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("MF", "AM"); // name=Saint Martin (French part) / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("PM", "AM"); // name=Saint Pierre and Miquelon / region=Americas / subregion=Northern America
        regionByCountryCodeMap.put("VC", "AM"); // name=Saint Vincent and the Grenadines / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("WS", "OC"); // name=Samoa / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("SM", "EU"); // name=San Marino / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("ST", "AF"); // name=Sao Tome and Principe / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("SA", "AS"); // name=Saudi Arabia / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("SN", "AF"); // name=Senegal / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("RS", "EU"); // name=Serbia / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("SC", "AF"); // name=Seychelles / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("SL", "AF"); // name=Sierra Leone / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("SG", "AS"); // name=Singapore / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("SX", "AM"); // name=Sint Maarten (Dutch part) / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("SK", "EU"); // name=Slovakia / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("SI", "EU"); // name=Slovenia / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("SB", "OC"); // name=Solomon Islands / region=Oceania / subregion=Melanesia
        regionByCountryCodeMap.put("SO", "AF"); // name=Somalia / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("ZA", "AF"); // name=South Africa / region=Africa / subregion=Southern Africa
        regionByCountryCodeMap.put("GS", "AM"); // name=South Georgia and the South Sandwich Islands / region=Americas / subregion=South America
        regionByCountryCodeMap.put("KR", "AS"); // name=Korea (Republic of) / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("SS", "AF"); // name=South Sudan / region=Africa / subregion=Middle Africa
        regionByCountryCodeMap.put("ES", "EU"); // name=Spain / region=Europe / subregion=Southern Europe
        regionByCountryCodeMap.put("LK", "AS"); // name=Sri Lanka / region=Asia / subregion=Southern Asia
        regionByCountryCodeMap.put("SD", "AF"); // name=Sudan / region=Africa / subregion=Northern Africa
        regionByCountryCodeMap.put("SR", "AM"); // name=Suriname / region=Americas / subregion=South America
        regionByCountryCodeMap.put("SJ", "EU"); // name=Svalbard and Jan Mayen / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("SZ", "AF"); // name=Swaziland / region=Africa / subregion=Southern Africa
        regionByCountryCodeMap.put("SE", "EU"); // name=Sweden / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("CH", "EU"); // name=Switzerland / region=Europe / subregion=Western Europe
        regionByCountryCodeMap.put("SY", "AS"); // name=Syrian Arab Republic / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("TW", "AS"); // name=Taiwan / region=Asia / subregion=Eastern Asia
        regionByCountryCodeMap.put("TJ", "AS"); // name=Tajikistan / region=Asia / subregion=Central Asia
        regionByCountryCodeMap.put("TZ", "AF"); // name=Tanzania, United Republic of / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("TH", "AS"); // name=Thailand / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("TL", "AS"); // name=Timor-Leste / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("TG", "AF"); // name=Togo / region=Africa / subregion=Western Africa
        regionByCountryCodeMap.put("TK", "OC"); // name=Tokelau / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("TO", "OC"); // name=Tonga / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("TT", "AM"); // name=Trinidad and Tobago / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("TN", "AF"); // name=Tunisia / region=Africa / subregion=Northern Africa
        regionByCountryCodeMap.put("TR", "AS"); // name=Turkey / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("TM", "AS"); // name=Turkmenistan / region=Asia / subregion=Central Asia
        regionByCountryCodeMap.put("TC", "AM"); // name=Turks and Caicos Islands / region=Americas / subregion=Caribbean
        regionByCountryCodeMap.put("TV", "OC"); // name=Tuvalu / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("UG", "AF"); // name=Uganda / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("UA", "EU"); // name=Ukraine / region=Europe / subregion=Eastern Europe
        regionByCountryCodeMap.put("AE", "AS"); // name=United Arab Emirates / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("GB", "EU"); // name=United Kingdom of Great Britain and Northern Ireland / region=Europe / subregion=Northern Europe
        regionByCountryCodeMap.put("US", "AM"); // name=United States of America / region=Americas / subregion=Northern America
        regionByCountryCodeMap.put("UY", "AM"); // name=Uruguay / region=Americas / subregion=South America
        regionByCountryCodeMap.put("UZ", "AS"); // name=Uzbekistan / region=Asia / subregion=Central Asia
        regionByCountryCodeMap.put("VU", "OC"); // name=Vanuatu / region=Oceania / subregion=Melanesia
        regionByCountryCodeMap.put("VE", "AM"); // name=Venezuela (Bolivarian Republic of) / region=Americas / subregion=South America
        regionByCountryCodeMap.put("VN", "AS"); // name=Viet Nam / region=Asia / subregion=South-Eastern Asia
        regionByCountryCodeMap.put("WF", "OC"); // name=Wallis and Futuna / region=Oceania / subregion=Polynesia
        regionByCountryCodeMap.put("EH", "AF"); // name=Western Sahara / region=Africa / subregion=Northern Africa
        regionByCountryCodeMap.put("YE", "AS"); // name=Yemen / region=Asia / subregion=Western Asia
        regionByCountryCodeMap.put("ZM", "AF"); // name=Zambia / region=Africa / subregion=Eastern Africa
        regionByCountryCodeMap.put("ZW", "AF"); // name=Zimbabwe / region=Africa / subregion=Eastern Africa
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
