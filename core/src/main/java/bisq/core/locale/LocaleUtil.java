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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocaleUtil {
    public static List<Locale> getAllLocales() {

        // Data from https://restcountries.eu/rest/v2/all?fields=name;region;subregion;alpha2Code;languages
        List<Locale> allLocales = new ArrayList<>();

        allLocales.add(Locale.of("ps", "AF")); // Afghanistan / lang=Pashto
        allLocales.add(Locale.of("sv", "AX")); // Åland Islands / lang=Swedish
        allLocales.add(Locale.of("sq", "AL")); // Albania / lang=Albanian
        allLocales.add(Locale.of("ar", "DZ")); // Algeria / lang=Arabic
        allLocales.add(Locale.of("en", "AS")); // American Samoa / lang=English
        allLocales.add(Locale.of("ca", "AD")); // Andorra / lang=Catalan
        allLocales.add(Locale.of("pt", "AO")); // Angola / lang=Portuguese
        allLocales.add(Locale.of("en", "AI")); // Anguilla / lang=English
        allLocales.add(Locale.of("en", "AG")); // Antigua and Barbuda / lang=English
        allLocales.add(Locale.of("es", "AR")); // Argentina / lang=Spanish
        allLocales.add(Locale.of("hy", "AM")); // Armenia / lang=Armenian
        allLocales.add(Locale.of("nl", "AW")); // Aruba / lang=Dutch
        allLocales.add(Locale.of("en", "AU")); // Australia / lang=English
        allLocales.add(Locale.of("de", "AT")); // Austria / lang=German
        allLocales.add(Locale.of("az", "AZ")); // Azerbaijan / lang=Azerbaijani
        allLocales.add(Locale.of("en", "BS")); // Bahamas / lang=English
        allLocales.add(Locale.of("ar", "BH")); // Bahrain / lang=Arabic
        allLocales.add(Locale.of("bn", "BD")); // Bangladesh / lang=Bengali
        allLocales.add(Locale.of("en", "BB")); // Barbados / lang=English
        allLocales.add(Locale.of("be", "BY")); // Belarus / lang=Belarusian
        allLocales.add(Locale.of("nl", "BE")); // Belgium / lang=Dutch
        allLocales.add(Locale.of("en", "BZ")); // Belize / lang=English
        allLocales.add(Locale.of("fr", "BJ")); // Benin / lang=French
        allLocales.add(Locale.of("en", "BM")); // Bermuda / lang=English
        allLocales.add(Locale.of("dz", "BT")); // Bhutan / lang=Dzongkha
        allLocales.add(Locale.of("es", "BO")); // Bolivia (Plurinational State of) / lang=Spanish
        allLocales.add(Locale.of("nl", "BQ")); // Bonaire, Sint Eustatius and Saba / lang=Dutch
        allLocales.add(Locale.of("bs", "BA")); // Bosnia and Herzegovina / lang=Bosnian
        allLocales.add(Locale.of("en", "BW")); // Botswana / lang=English
        allLocales.add(Locale.of("pt", "BR")); // Brazil / lang=Portuguese
        allLocales.add(Locale.of("en", "IO")); // British Indian Ocean Territory / lang=English
        allLocales.add(Locale.of("en", "UM")); // United States Minor Outlying Islands / lang=English
        allLocales.add(Locale.of("en", "VG")); // Virgin Islands (British) / lang=English
        allLocales.add(Locale.of("en", "VI")); // Virgin Islands (U.S.) / lang=English
        allLocales.add(Locale.of("ms", "BN")); // Brunei Darussalam / lang=Malay
        allLocales.add(Locale.of("bg", "BG")); // Bulgaria / lang=Bulgarian
        allLocales.add(Locale.of("fr", "BF")); // Burkina Faso / lang=French
        allLocales.add(Locale.of("fr", "BI")); // Burundi / lang=French
        allLocales.add(Locale.of("km", "KH")); // Cambodia / lang=Khmer
        allLocales.add(Locale.of("en", "CM")); // Cameroon / lang=English
        allLocales.add(Locale.of("en", "CA")); // Canada / lang=English
        allLocales.add(Locale.of("pt", "CV")); // Cabo Verde / lang=Portuguese
        allLocales.add(Locale.of("en", "KY")); // Cayman Islands / lang=English
        allLocales.add(Locale.of("fr", "CF")); // Central African Republic / lang=French
        allLocales.add(Locale.of("fr", "TD")); // Chad / lang=French
        allLocales.add(Locale.of("es", "CL")); // Chile / lang=Spanish
        allLocales.add(Locale.of("zh", "CN")); // China / lang=Chinese
        allLocales.add(Locale.of("en", "CX")); // Christmas Island / lang=English
        allLocales.add(Locale.of("en", "CC")); // Cocos (Keeling) Islands / lang=English
        allLocales.add(Locale.of("es", "CO")); // Colombia / lang=Spanish
        allLocales.add(Locale.of("ar", "KM")); // Comoros / lang=Arabic
        allLocales.add(Locale.of("fr", "CG")); // Congo / lang=French
        allLocales.add(Locale.of("fr", "CD")); // Congo (Democratic Republic of the) / lang=French
        allLocales.add(Locale.of("en", "CK")); // Cook Islands / lang=English
        allLocales.add(Locale.of("es", "CR")); // Costa Rica / lang=Spanish
        allLocales.add(Locale.of("hr", "HR")); // Croatia / lang=Croatian
        allLocales.add(Locale.of("es", "CU")); // Cuba / lang=Spanish
        allLocales.add(Locale.of("nl", "CW")); // Curaçao / lang=Dutch
        allLocales.add(Locale.of("el", "CY")); // Cyprus / lang=Greek (modern)
        allLocales.add(Locale.of("cs", "CZ")); // Czech Republic / lang=Czech
        allLocales.add(Locale.of("da", "DK")); // Denmark / lang=Danish
        allLocales.add(Locale.of("fr", "DJ")); // Djibouti / lang=French
        allLocales.add(Locale.of("en", "DM")); // Dominica / lang=English
        allLocales.add(Locale.of("es", "DO")); // Dominican Republic / lang=Spanish
        allLocales.add(Locale.of("es", "EC")); // Ecuador / lang=Spanish
        allLocales.add(Locale.of("ar", "EG")); // Egypt / lang=Arabic
        allLocales.add(Locale.of("es", "SV")); // El Salvador / lang=Spanish
        allLocales.add(Locale.of("es", "GQ")); // Equatorial Guinea / lang=Spanish
        allLocales.add(Locale.of("ti", "ER")); // Eritrea / lang=Tigrinya
        allLocales.add(Locale.of("et", "EE")); // Estonia / lang=Estonian
        allLocales.add(Locale.of("am", "ET")); // Ethiopia / lang=Amharic
        allLocales.add(Locale.of("en", "FK")); // Falkland Islands (Malvinas) / lang=English
        allLocales.add(Locale.of("fo", "FO")); // Faroe Islands / lang=Faroese
        allLocales.add(Locale.of("en", "FJ")); // Fiji / lang=English
        allLocales.add(Locale.of("fi", "FI")); // Finland / lang=Finnish
        allLocales.add(Locale.of("fr", "FR")); // France / lang=French
        allLocales.add(Locale.of("fr", "GF")); // French Guiana / lang=French
        allLocales.add(Locale.of("fr", "PF")); // French Polynesia / lang=French
        allLocales.add(Locale.of("fr", "TF")); // French Southern Territories / lang=French
        allLocales.add(Locale.of("fr", "GA")); // Gabon / lang=French
        allLocales.add(Locale.of("en", "GM")); // Gambia / lang=English
        allLocales.add(Locale.of("ka", "GE")); // Georgia / lang=Georgian
        allLocales.add(Locale.of("de", "DE")); // Germany / lang=German
        allLocales.add(Locale.of("en", "GH")); // Ghana / lang=English
        allLocales.add(Locale.of("en", "GI")); // Gibraltar / lang=English
        allLocales.add(Locale.of("el", "GR")); // Greece / lang=Greek (modern)
        allLocales.add(Locale.of("kl", "GL")); // Greenland / lang=Kalaallisut
        allLocales.add(Locale.of("en", "GD")); // Grenada / lang=English
        allLocales.add(Locale.of("fr", "GP")); // Guadeloupe / lang=French
        allLocales.add(Locale.of("en", "GU")); // Guam / lang=English
        allLocales.add(Locale.of("es", "GT")); // Guatemala / lang=Spanish
        allLocales.add(Locale.of("en", "GG")); // Guernsey / lang=English
        allLocales.add(Locale.of("fr", "GN")); // Guinea / lang=French
        allLocales.add(Locale.of("pt", "GW")); // Guinea-Bissau / lang=Portuguese
        allLocales.add(Locale.of("en", "GY")); // Guyana / lang=English
        allLocales.add(Locale.of("fr", "HT")); // Haiti / lang=French
        allLocales.add(Locale.of("la", "VA")); // Holy See / lang=Latin
        allLocales.add(Locale.of("es", "HN")); // Honduras / lang=Spanish
        allLocales.add(Locale.of("en", "HK")); // Hong Kong / lang=English
        allLocales.add(Locale.of("hu", "HU")); // Hungary / lang=Hungarian
        allLocales.add(Locale.of("is", "IS")); // Iceland / lang=Icelandic
        allLocales.add(Locale.of("hi", "IN")); // India / lang=Hindi
        allLocales.add(Locale.of("id", "ID")); // Indonesia / lang=Indonesian
        allLocales.add(Locale.of("fr", "CI")); // Côte d'Ivoire / lang=French
        allLocales.add(Locale.of("fa", "IR")); // Iran (Islamic Republic of) / lang=Persian (Farsi)
        allLocales.add(Locale.of("ar", "IQ")); // Iraq / lang=Arabic
        allLocales.add(Locale.of("ga", "IE")); // Ireland / lang=Irish
        allLocales.add(Locale.of("en", "IM")); // Isle of Man / lang=English
        allLocales.add(Locale.of("he", "IL")); // Israel / lang=Hebrew (modern)
        allLocales.add(Locale.of("it", "IT")); // Italy / lang=Italian
        allLocales.add(Locale.of("en", "JM")); // Jamaica / lang=English
        allLocales.add(Locale.of("ja", "JP")); // Japan / lang=Japanese
        allLocales.add(Locale.of("en", "JE")); // Jersey / lang=English
        allLocales.add(Locale.of("ar", "JO")); // Jordan / lang=Arabic
        allLocales.add(Locale.of("kk", "KZ")); // Kazakhstan / lang=Kazakh
        allLocales.add(Locale.of("en", "KE")); // Kenya / lang=English
        allLocales.add(Locale.of("en", "KI")); // Kiribati / lang=English
        allLocales.add(Locale.of("ar", "KW")); // Kuwait / lang=Arabic
        allLocales.add(Locale.of("ky", "KG")); // Kyrgyzstan / lang=Kyrgyz
        allLocales.add(Locale.of("lo", "LA")); // Lao People's Democratic Republic / lang=Lao
        allLocales.add(Locale.of("lv", "LV")); // Latvia / lang=Latvian
        allLocales.add(Locale.of("ar", "LB")); // Lebanon / lang=Arabic
        allLocales.add(Locale.of("en", "LS")); // Lesotho / lang=English
        allLocales.add(Locale.of("en", "LR")); // Liberia / lang=English
        allLocales.add(Locale.of("ar", "LY")); // Libya / lang=Arabic
        allLocales.add(Locale.of("de", "LI")); // Liechtenstein / lang=German
        allLocales.add(Locale.of("lt", "LT")); // Lithuania / lang=Lithuanian
        allLocales.add(Locale.of("fr", "LU")); // Luxembourg / lang=French
        allLocales.add(Locale.of("zh", "MO")); // Macao / lang=Chinese
        allLocales.add(Locale.of("mk", "MK")); // Macedonia (the former Yugoslav Republic of) / lang=Macedonian
        allLocales.add(Locale.of("fr", "MG")); // Madagascar / lang=French
        allLocales.add(Locale.of("en", "MW")); // Malawi / lang=English
        allLocales.add(Locale.of("en", "MY")); // Malaysia / lang=Malaysian
        allLocales.add(Locale.of("dv", "MV")); // Maldives / lang=Divehi
        allLocales.add(Locale.of("fr", "ML")); // Mali / lang=French
        allLocales.add(Locale.of("mt", "MT")); // Malta / lang=Maltese
        allLocales.add(Locale.of("en", "MH")); // Marshall Islands / lang=English
        allLocales.add(Locale.of("fr", "MQ")); // Martinique / lang=French
        allLocales.add(Locale.of("ar", "MR")); // Mauritania / lang=Arabic
        allLocales.add(Locale.of("en", "MU")); // Mauritius / lang=English
        allLocales.add(Locale.of("fr", "YT")); // Mayotte / lang=French
        allLocales.add(Locale.of("es", "MX")); // Mexico / lang=Spanish
        allLocales.add(Locale.of("en", "FM")); // Micronesia (Federated States of) / lang=English
        allLocales.add(Locale.of("ro", "MD")); // Moldova (Republic of) / lang=Romanian
        allLocales.add(Locale.of("fr", "MC")); // Monaco / lang=French
        allLocales.add(Locale.of("mn", "MN")); // Mongolia / lang=Mongolian
        allLocales.add(Locale.of("sr", "ME")); // Montenegro / lang=Serbian
        allLocales.add(Locale.of("en", "MS")); // Montserrat / lang=English
        allLocales.add(Locale.of("ar", "MA")); // Morocco / lang=Arabic
        allLocales.add(Locale.of("pt", "MZ")); // Mozambique / lang=Portuguese
        allLocales.add(Locale.of("my", "MM")); // Myanmar / lang=Burmese
        allLocales.add(Locale.of("en", "NA")); // Namibia / lang=English
        allLocales.add(Locale.of("en", "NR")); // Nauru / lang=English
        allLocales.add(Locale.of("ne", "NP")); // Nepal / lang=Nepali
        allLocales.add(Locale.of("nl", "NL")); // Netherlands / lang=Dutch
        allLocales.add(Locale.of("fr", "NC")); // New Caledonia / lang=French
        allLocales.add(Locale.of("en", "NZ")); // New Zealand / lang=English
        allLocales.add(Locale.of("es", "NI")); // Nicaragua / lang=Spanish
        allLocales.add(Locale.of("fr", "NE")); // Niger / lang=French
        allLocales.add(Locale.of("en", "NG")); // Nigeria / lang=English
        allLocales.add(Locale.of("en", "NU")); // Niue / lang=English
        allLocales.add(Locale.of("en", "NF")); // Norfolk Island / lang=English
        allLocales.add(Locale.of("ko", "KP")); // Korea (Democratic People's Republic of) / lang=Korean
        allLocales.add(Locale.of("en", "MP")); // Northern Mariana Islands / lang=English
        allLocales.add(Locale.of("no", "NO")); // Norway / lang=Norwegian
        allLocales.add(Locale.of("ar", "OM")); // Oman / lang=Arabic
        allLocales.add(Locale.of("en", "PK")); // Pakistan / lang=English
        allLocales.add(Locale.of("en", "PW")); // Palau / lang=English
        allLocales.add(Locale.of("ar", "PS")); // Palestine, State of / lang=Arabic
        allLocales.add(Locale.of("es", "PA")); // Panama / lang=Spanish
        allLocales.add(Locale.of("en", "PG")); // Papua New Guinea / lang=English
        allLocales.add(Locale.of("es", "PY")); // Paraguay / lang=Spanish
        allLocales.add(Locale.of("es", "PE")); // Peru / lang=Spanish
        allLocales.add(Locale.of("en", "PH")); // Philippines / lang=English
        allLocales.add(Locale.of("en", "PN")); // Pitcairn / lang=English
        allLocales.add(Locale.of("pl", "PL")); // Poland / lang=Polish
        allLocales.add(Locale.of("pt", "PT")); // Portugal / lang=Portuguese
        allLocales.add(Locale.of("es", "PR")); // Puerto Rico / lang=Spanish
        allLocales.add(Locale.of("ar", "QA")); // Qatar / lang=Arabic
        allLocales.add(Locale.of("sq", "XK")); // Republic of Kosovo / lang=Albanian
        allLocales.add(Locale.of("fr", "RE")); // Réunion / lang=French
        allLocales.add(Locale.of("ro", "RO")); // Romania / lang=Romanian
        allLocales.add(Locale.of("ru", "RU")); // Russian Federation / lang=Russian
        allLocales.add(Locale.of("rw", "RW")); // Rwanda / lang=Kinyarwanda
        allLocales.add(Locale.of("fr", "BL")); // Saint Barthélemy / lang=French
        allLocales.add(Locale.of("en", "SH")); // Saint Helena, Ascension and Tristan da Cunha / lang=English
        allLocales.add(Locale.of("en", "KN")); // Saint Kitts and Nevis / lang=English
        allLocales.add(Locale.of("en", "LC")); // Saint Lucia / lang=English
        allLocales.add(Locale.of("en", "MF")); // Saint Martin (French part) / lang=English
        allLocales.add(Locale.of("fr", "PM")); // Saint Pierre and Miquelon / lang=French
        allLocales.add(Locale.of("en", "VC")); // Saint Vincent and the Grenadines / lang=English
        allLocales.add(Locale.of("sm", "WS")); // Samoa / lang=Samoan
        allLocales.add(Locale.of("it", "SM")); // San Marino / lang=Italian
        allLocales.add(Locale.of("pt", "ST")); // Sao Tome and Principe / lang=Portuguese
        allLocales.add(Locale.of("ar", "SA")); // Saudi Arabia / lang=Arabic
        allLocales.add(Locale.of("fr", "SN")); // Senegal / lang=French
        allLocales.add(Locale.of("sr", "RS")); // Serbia / lang=Serbian
        allLocales.add(Locale.of("fr", "SC")); // Seychelles / lang=French
        allLocales.add(Locale.of("en", "SL")); // Sierra Leone / lang=English
        allLocales.add(Locale.of("en", "SG")); // Singapore / lang=English
        allLocales.add(Locale.of("nl", "SX")); // Sint Maarten (Dutch part) / lang=Dutch
        allLocales.add(Locale.of("sk", "SK")); // Slovakia / lang=Slovak
        allLocales.add(Locale.of("sl", "SI")); // Slovenia / lang=Slovene
        allLocales.add(Locale.of("en", "SB")); // Solomon Islands / lang=English
        allLocales.add(Locale.of("so", "SO")); // Somalia / lang=Somali
        allLocales.add(Locale.of("af", "ZA")); // South Africa / lang=Afrikaans
        allLocales.add(Locale.of("en", "GS")); // South Georgia and the South Sandwich Islands / lang=English
        allLocales.add(Locale.of("ko", "KR")); // Korea (Republic of) / lang=Korean
        allLocales.add(Locale.of("en", "SS")); // South Sudan / lang=English
        allLocales.add(Locale.of("es", "ES")); // Spain / lang=Spanish
        allLocales.add(Locale.of("si", "LK")); // Sri Lanka / lang=Sinhalese
        allLocales.add(Locale.of("ar", "SD")); // Sudan / lang=Arabic
        allLocales.add(Locale.of("nl", "SR")); // Suriname / lang=Dutch
        allLocales.add(Locale.of("no", "SJ")); // Svalbard and Jan Mayen / lang=Norwegian
        allLocales.add(Locale.of("en", "SZ")); // Swaziland / lang=English
        allLocales.add(Locale.of("sv", "SE")); // Sweden / lang=Swedish
        allLocales.add(Locale.of("de", "CH")); // Switzerland / lang=German
        allLocales.add(Locale.of("ar", "SY")); // Syrian Arab Republic / lang=Arabic
        allLocales.add(Locale.of("zh", "TW")); // Taiwan / lang=Chinese
        allLocales.add(Locale.of("tg", "TJ")); // Tajikistan / lang=Tajik
        allLocales.add(Locale.of("sw", "TZ")); // Tanzania, United Republic of / lang=Swahili
        allLocales.add(Locale.of("th", "TH")); // Thailand / lang=Thai
        allLocales.add(Locale.of("pt", "TL")); // Timor-Leste / lang=Portuguese
        allLocales.add(Locale.of("fr", "TG")); // Togo / lang=French
        allLocales.add(Locale.of("en", "TK")); // Tokelau / lang=English
        allLocales.add(Locale.of("en", "TO")); // Tonga / lang=English
        allLocales.add(Locale.of("en", "TT")); // Trinidad and Tobago / lang=English
        allLocales.add(Locale.of("ar", "TN")); // Tunisia / lang=Arabic
        allLocales.add(Locale.of("tr", "TR")); // Turkey / lang=Turkish
        allLocales.add(Locale.of("tk", "TM")); // Turkmenistan / lang=Turkmen
        allLocales.add(Locale.of("en", "TC")); // Turks and Caicos Islands / lang=English
        allLocales.add(Locale.of("en", "TV")); // Tuvalu / lang=English
        allLocales.add(Locale.of("en", "UG")); // Uganda / lang=English
        allLocales.add(Locale.of("uk", "UA")); // Ukraine / lang=Ukrainian
        allLocales.add(Locale.of("ar", "AE")); // United Arab Emirates / lang=Arabic
        allLocales.add(Locale.of("en", "GB")); // United Kingdom of Great Britain and Northern Ireland / lang=English
        allLocales.add(Locale.of("en", "US")); // United States of America / lang=English
        allLocales.add(Locale.of("es", "UY")); // Uruguay / lang=Spanish
        allLocales.add(Locale.of("uz", "UZ")); // Uzbekistan / lang=Uzbek
        allLocales.add(Locale.of("bi", "VU")); // Vanuatu / lang=Bislama
        allLocales.add(Locale.of("es", "VE")); // Venezuela (Bolivarian Republic of) / lang=Spanish
        allLocales.add(Locale.of("vi", "VN")); // Viet Nam / lang=Vietnamese
        allLocales.add(Locale.of("fr", "WF")); // Wallis and Futuna / lang=French
        allLocales.add(Locale.of("es", "EH")); // Western Sahara / lang=Spanish
        allLocales.add(Locale.of("ar", "YE")); // Yemen / lang=Arabic
        allLocales.add(Locale.of("en", "ZM")); // Zambia / lang=English
        allLocales.add(Locale.of("en", "ZW")); // Zimbabwe / lang=English

        return allLocales;
    }
}
