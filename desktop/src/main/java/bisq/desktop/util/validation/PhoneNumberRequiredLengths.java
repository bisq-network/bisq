package bisq.desktop.util.validation;

import java.util.Map;

import static java.util.Map.entry;

final class PhoneNumberRequiredLengths {
    /**
     * Immutable mapping of ISO 3166 alpha-2 country code to the required length of the phone number (in digits, exluding country code prefix).
     *
     * Used by PhoneNumberValidator.java 
     * If an entry is not present in the map, the requiredLength option will not be used in the validation.
     */
    private final static Map<String, Integer> NUMBER_LENGTH_MAP = Map.ofEntries(
            entry("AG", 7 ),  // CountryCode: "1-268"
            entry("AI", 7 ),  // CountryCode: "1-264"
            entry("AS", 7 ),  // CountryCode: "1-684"
            entry("BB", 7 ),  // CountryCode: "1-246",
            entry("BM", 7 ),  // CountryCode: "1-441"
            entry("BS", 7 ),  // CountryCode: "1-242"
            entry("CA", 10),  // CountryCode: "1"
            entry("DM", 7 ),  // CountryCode: "1-767"
            entry("DO", 10),  // CountryCode: "1"     (DO has three area codes 809,829,849; let user define hers)
            entry("GD", 7 ),  // CountryCode: "1-473"
            entry("GU", 7 ),  // CountryCode: "1-671"
            entry("JM", 10 ), // CountryCode: "1" (has two codes 876, 658)
            entry("KN", 7 ),  // CountryCode: "1-869"
            entry("KY", 7 ),  // CountryCode: "1-345"
            entry("KZ", 10),  // CountryCode: "7"
            entry("LC", 7 ),  // CountryCode: "1-758"
            entry("MP", 7 ),  // CountryCode: "1-670"
            entry("MS", 7 ),  // CountryCode: "1-664"
            entry("PR", 10),  // CountryCode: "1" (has two codes 787, 939)
            entry("RU", 10),  // CountryCode: "7"
            entry("SX", 7 ),  // CountryCode: "1-721"
            entry("TC", 7 ),  // CountryCode: "1-649"
            entry("TT", 7 ),  // CountryCode: "1-868"
            entry("US", 10),  // CountryCode: "1"
            entry("VC", 7 ),  // CountryCode: "1-784"
            entry("VG", 7 ),  // CountryCode: "1-284"
            entry("VI", 7 )   // CountryCode: "1-340"
    );

    static Integer getRequiredLength(String isoCountryCode) {
        return NUMBER_LENGTH_MAP.get(isoCountryCode);
    }
}
