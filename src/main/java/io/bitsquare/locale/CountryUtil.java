package io.bitsquare.locale;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import java.util.*;
import java.util.stream.Collectors;

public class CountryUtil
{
    private static final String[] countryCodes = new String[]{"AE",
            "AL",
            "AR",
            "AT",
            "AU",
            "BA",
            "BE",
            "BG",
            "BH",
            "BO",
            "BR",
            "BY",
            "CA",
            "CH",
            "CL",
            "CN",
            "CO",
            "CR",
            "CS",
            "CU",
            "CY",
            "CZ",
            "DE",
            "DK",
            "DO",
            "DZ",
            "EC",
            "EE",
            "EG",
            "ES",
            "FI",
            "FR",
            "GB",
            "GR",
            "GT",
            "HK",
            "HN",
            "HR",
            "HU",
            "ID",
            "IE",
            "IL",
            "IN",
            "IQ",
            "IS",
            "IT",
            "JO",
            "JP",
            "KR",
            "KW",
            "LB",
            "LT",
            "LU",
            "LV",
            "LY",
            "MA",
            "ME",
            "MK",
            "MT",
            "MX",
            "MY",
            "NI",
            "NL",
            "NO",
            "NZ",
            "OM",
            "PA",
            "PE",
            "PH",
            "PL",
            "PR",
            "PT",
            "PY",
            "QA",
            "RO",
            "RS",
            "RU",
            "SA",
            "SD",
            "SE",
            "SG",
            "SI",
            "SK",
            "SV",
            "SY",
            "TH",
            "TN",
            "TR",
            "TW",
            "UA",
            "US",
            "UY",
            "VE",
            "VN",
            "YE",
            "ZA"
    };
    private static final List<String> regionCodeList = Arrays.asList(countryCodes);
    private static final String[] regionCodes = new String[]{
            "AS",
            "EU",
            "SA",
            "EU",
            "OC",
            "EU",
            "EU",
            "EU",
            "AS",
            "SA",
            "SA",
            "EU",
            "NA",
            "EU",
            "SA",
            "AS",
            "SA",
            "NA",
            "EU",
            "NA",
            "AS",
            "EU",
            "EU",
            "EU",
            "NA",
            "AF",
            "SA",
            "EU",
            "AF",
            "EU",
            "EU",
            "EU",
            "EU",
            "EU",
            "NA",
            "AS",
            "NA",
            "EU",
            "EU",
            "AS",
            "EU",
            "AS",
            "AS",
            "AS",
            "EU",
            "EU",
            "AS",
            "AS",
            "AS",
            "AS",
            "AS",
            "EU",
            "EU",
            "EU",
            "AF",
            "AF",
            "EU",
            "EU",
            "EU",
            "NA",
            "AS",
            "NA",
            "EU",
            "EU",
            "OC",
            "AS",
            "NA",
            "SA",
            "AS",
            "EU",
            "NA",
            "EU",
            "SA",
            "AS",
            "EU",
            "EU",
            "EU",
            "AS",
            "AF",
            "EU",
            "AS",
            "EU",
            "EU",
            "NA",
            "AS",
            "AS",
            "AF",
            "AS",
            "AS",
            "EU",
            "NA",
            "SA",
            "SA",
            "AS",
            "AS",
            "AF"
    };
    private static final List<String> countryCodeList = Arrays.asList(regionCodes);
    private static final String[][] regionCodeToName = new String[][]{
            {"NA", "North America"},
            {"SA", "South America"},
            {"AF", "Africa"},
            {"EU", "Europe"},
            {"AS", "Asia"},
            {"OC", "Oceania"}
    };

    public static List<Region> getAllRegions()
    {
        List<Region> allRegions = new ArrayList<>();

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

    public static List<Country> getAllCountriesFor(Region selectedRegion)
    {
        List<Country> allCountries = getAllCountries();
        return Lists.newArrayList(Collections2.filter(allCountries, country -> selectedRegion != null && country != null && country.getRegion().equals(selectedRegion)));
    }

    public static List<Country> getAllCountries()
    {
        List<Locale> allCountryLocales = getAllCountryLocales();
        List<Country> allCountries = new ArrayList<>();

        for (Locale locale : allCountryLocales)
        {
            String regionCode = getRegionCode(locale.getCountry());
            Region region = new Region(regionCode, getRegionName(regionCode));
            Country country = new Country(locale.getCountry(), locale.getDisplayCountry(), region);
            allCountries.add(country);
        }
        return allCountries;
    }

    public static Country getDefaultCountry()
    {
        Locale locale = new Locale("", Locale.getDefault().getCountry());
        String regionCode = getRegionCode(locale.getCountry());
        Region region = new Region(regionCode, getRegionName(regionCode));
        return new Country(locale.getCountry(), locale.getDisplayCountry(), region);
    }

    public static String getRegionName(String regionCode)
    {
        for (String[] regionName : regionCodeToName)
        {
            if (regionName[0].equals(regionCode))
            {
                return regionName[1];
            }
        }
        return regionCode;
    }

    private static List<Locale> getAllCountryLocales()
    {
        List<Locale> allLocales = Arrays.asList(Locale.getAvailableLocales());

        Set<Locale> allLocalesAsSet = allLocales.stream().filter(locale -> !locale.getCountry().equals("")).map(locale -> new Locale("", locale.getCountry(), "")).collect(Collectors.toSet());
        /*
        same as:
        Set<Locale> allLocalesAsSet = new HashSet<>();
        for (Locale locale : allLocales)
        {
            if (!locale.getCountry().equals(""))
            {
                allLocalesAsSet.add(new Locale("", locale.getCountry(), ""));
            }
        }  */

        allLocales = new ArrayList<>();
        allLocales.addAll(allLocalesAsSet);
        allLocales.sort((locale1, locale2) -> locale1.getDisplayCountry().compareTo(locale2.getDisplayCountry()));
          /*
            allLocales.sort(new Comparator<Locale>()
        {
            @Override
            public int compare(Locale locale1, Locale locale2)
            {
                return locale1.getDisplayCountry().compareTo(locale2.getDisplayCountry());
            }
        });

           */
        return allLocales;
    }

    private static String getRegionCode(String countryCode)
    {
        if (countryCode.length() > 0 && countryCodeList.contains(countryCode))
        {
            int index = countryCodeList.indexOf(countryCode);
            return regionCodeList.get(index);
        }
        else
        {
            return "Undefined";
        }
    }

}
