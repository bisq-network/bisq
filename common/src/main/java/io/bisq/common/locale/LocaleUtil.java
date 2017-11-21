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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocaleUtil {
    private static final Logger log = LoggerFactory.getLogger(LocaleUtil.class);

    public static List<Locale> getAllLocales() {

        // Data from https://restcountries.eu/rest/v2/all?fields=name;region;subregion;alpha2Code;languages
        List<Locale> allLocales = new ArrayList<>();

        allLocales.add(new Locale("ps", "AF")); // Afghanistan / lang=Pashto
        allLocales.add(new Locale("sv", "AX")); // Åland Islands / lang=Swedish
        allLocales.add(new Locale("sq", "AL")); // Albania / lang=Albanian
        allLocales.add(new Locale("ar", "DZ")); // Algeria / lang=Arabic
        allLocales.add(new Locale("en", "AS")); // American Samoa / lang=English
        allLocales.add(new Locale("ca", "AD")); // Andorra / lang=Catalan
        allLocales.add(new Locale("pt", "AO")); // Angola / lang=Portuguese
        allLocales.add(new Locale("en", "AI")); // Anguilla / lang=English
        allLocales.add(new Locale("en", "AG")); // Antigua and Barbuda / lang=English
        allLocales.add(new Locale("es", "AR")); // Argentina / lang=Spanish
        allLocales.add(new Locale("hy", "AM")); // Armenia / lang=Armenian
        allLocales.add(new Locale("nl", "AW")); // Aruba / lang=Dutch
        allLocales.add(new Locale("en", "AU")); // Australia / lang=English
        allLocales.add(new Locale("de", "AT")); // Austria / lang=German
        allLocales.add(new Locale("az", "AZ")); // Azerbaijan / lang=Azerbaijani
        allLocales.add(new Locale("en", "BS")); // Bahamas / lang=English
        allLocales.add(new Locale("ar", "BH")); // Bahrain / lang=Arabic
        allLocales.add(new Locale("bn", "BD")); // Bangladesh / lang=Bengali
        allLocales.add(new Locale("en", "BB")); // Barbados / lang=English
        allLocales.add(new Locale("be", "BY")); // Belarus / lang=Belarusian
        allLocales.add(new Locale("nl", "BE")); // Belgium / lang=Dutch
        allLocales.add(new Locale("en", "BZ")); // Belize / lang=English
        allLocales.add(new Locale("fr", "BJ")); // Benin / lang=French
        allLocales.add(new Locale("en", "BM")); // Bermuda / lang=English
        allLocales.add(new Locale("dz", "BT")); // Bhutan / lang=Dzongkha
        allLocales.add(new Locale("es", "BO")); // Bolivia (Plurinational State of) / lang=Spanish
        allLocales.add(new Locale("nl", "BQ")); // Bonaire, Sint Eustatius and Saba / lang=Dutch
        allLocales.add(new Locale("bs", "BA")); // Bosnia and Herzegovina / lang=Bosnian
        allLocales.add(new Locale("en", "BW")); // Botswana / lang=English
        allLocales.add(new Locale("pt", "BR")); // Brazil / lang=Portuguese
        allLocales.add(new Locale("en", "IO")); // British Indian Ocean Territory / lang=English
        allLocales.add(new Locale("en", "UM")); // United States Minor Outlying Islands / lang=English
        allLocales.add(new Locale("en", "VG")); // Virgin Islands (British) / lang=English
        allLocales.add(new Locale("en", "VI")); // Virgin Islands (U.S.) / lang=English
        allLocales.add(new Locale("ms", "BN")); // Brunei Darussalam / lang=Malay
        allLocales.add(new Locale("bg", "BG")); // Bulgaria / lang=Bulgarian
        allLocales.add(new Locale("fr", "BF")); // Burkina Faso / lang=French
        allLocales.add(new Locale("fr", "BI")); // Burundi / lang=French
        allLocales.add(new Locale("km", "KH")); // Cambodia / lang=Khmer
        allLocales.add(new Locale("en", "CM")); // Cameroon / lang=English
        allLocales.add(new Locale("en", "CA")); // Canada / lang=English
        allLocales.add(new Locale("pt", "CV")); // Cabo Verde / lang=Portuguese
        allLocales.add(new Locale("en", "KY")); // Cayman Islands / lang=English
        allLocales.add(new Locale("fr", "CF")); // Central African Republic / lang=French
        allLocales.add(new Locale("fr", "TD")); // Chad / lang=French
        allLocales.add(new Locale("es", "CL")); // Chile / lang=Spanish
        allLocales.add(new Locale("zh", "CN")); // China / lang=Chinese
        allLocales.add(new Locale("en", "CX")); // Christmas Island / lang=English
        allLocales.add(new Locale("en", "CC")); // Cocos (Keeling) Islands / lang=English
        allLocales.add(new Locale("es", "CO")); // Colombia / lang=Spanish
        allLocales.add(new Locale("ar", "KM")); // Comoros / lang=Arabic
        allLocales.add(new Locale("fr", "CG")); // Congo / lang=French
        allLocales.add(new Locale("fr", "CD")); // Congo (Democratic Republic of the) / lang=French
        allLocales.add(new Locale("en", "CK")); // Cook Islands / lang=English
        allLocales.add(new Locale("es", "CR")); // Costa Rica / lang=Spanish
        allLocales.add(new Locale("hr", "HR")); // Croatia / lang=Croatian
        allLocales.add(new Locale("es", "CU")); // Cuba / lang=Spanish
        allLocales.add(new Locale("nl", "CW")); // Curaçao / lang=Dutch
        allLocales.add(new Locale("el", "CY")); // Cyprus / lang=Greek (modern)
        allLocales.add(new Locale("cs", "CZ")); // Czech Republic / lang=Czech
        allLocales.add(new Locale("da", "DK")); // Denmark / lang=Danish
        allLocales.add(new Locale("fr", "DJ")); // Djibouti / lang=French
        allLocales.add(new Locale("en", "DM")); // Dominica / lang=English
        allLocales.add(new Locale("es", "DO")); // Dominican Republic / lang=Spanish
        allLocales.add(new Locale("es", "EC")); // Ecuador / lang=Spanish
        allLocales.add(new Locale("ar", "EG")); // Egypt / lang=Arabic
        allLocales.add(new Locale("es", "SV")); // El Salvador / lang=Spanish
        allLocales.add(new Locale("es", "GQ")); // Equatorial Guinea / lang=Spanish
        allLocales.add(new Locale("ti", "ER")); // Eritrea / lang=Tigrinya
        allLocales.add(new Locale("et", "EE")); // Estonia / lang=Estonian
        allLocales.add(new Locale("am", "ET")); // Ethiopia / lang=Amharic
        allLocales.add(new Locale("en", "FK")); // Falkland Islands (Malvinas) / lang=English
        allLocales.add(new Locale("fo", "FO")); // Faroe Islands / lang=Faroese
        allLocales.add(new Locale("en", "FJ")); // Fiji / lang=English
        allLocales.add(new Locale("fi", "FI")); // Finland / lang=Finnish
        allLocales.add(new Locale("fr", "FR")); // France / lang=French
        allLocales.add(new Locale("fr", "GF")); // French Guiana / lang=French
        allLocales.add(new Locale("fr", "PF")); // French Polynesia / lang=French
        allLocales.add(new Locale("fr", "TF")); // French Southern Territories / lang=French
        allLocales.add(new Locale("fr", "GA")); // Gabon / lang=French
        allLocales.add(new Locale("en", "GM")); // Gambia / lang=English
        allLocales.add(new Locale("ka", "GE")); // Georgia / lang=Georgian
        allLocales.add(new Locale("de", "DE")); // Germany / lang=German
        allLocales.add(new Locale("en", "GH")); // Ghana / lang=English
        allLocales.add(new Locale("en", "GI")); // Gibraltar / lang=English
        allLocales.add(new Locale("el", "GR")); // Greece / lang=Greek (modern)
        allLocales.add(new Locale("kl", "GL")); // Greenland / lang=Kalaallisut
        allLocales.add(new Locale("en", "GD")); // Grenada / lang=English
        allLocales.add(new Locale("fr", "GP")); // Guadeloupe / lang=French
        allLocales.add(new Locale("en", "GU")); // Guam / lang=English
        allLocales.add(new Locale("es", "GT")); // Guatemala / lang=Spanish
        allLocales.add(new Locale("en", "GG")); // Guernsey / lang=English
        allLocales.add(new Locale("fr", "GN")); // Guinea / lang=French
        allLocales.add(new Locale("pt", "GW")); // Guinea-Bissau / lang=Portuguese
        allLocales.add(new Locale("en", "GY")); // Guyana / lang=English
        allLocales.add(new Locale("fr", "HT")); // Haiti / lang=French
        allLocales.add(new Locale("la", "VA")); // Holy See / lang=Latin
        allLocales.add(new Locale("es", "HN")); // Honduras / lang=Spanish
        allLocales.add(new Locale("en", "HK")); // Hong Kong / lang=English
        allLocales.add(new Locale("hu", "HU")); // Hungary / lang=Hungarian
        allLocales.add(new Locale("is", "IS")); // Iceland / lang=Icelandic
        allLocales.add(new Locale("hi", "IN")); // India / lang=Hindi
        allLocales.add(new Locale("id", "ID")); // Indonesia / lang=Indonesian
        allLocales.add(new Locale("fr", "CI")); // Côte d'Ivoire / lang=French
        allLocales.add(new Locale("fa", "IR")); // Iran (Islamic Republic of) / lang=Persian (Farsi)
        allLocales.add(new Locale("ar", "IQ")); // Iraq / lang=Arabic
        allLocales.add(new Locale("ga", "IE")); // Ireland / lang=Irish
        allLocales.add(new Locale("en", "IM")); // Isle of Man / lang=English
        allLocales.add(new Locale("he", "IL")); // Israel / lang=Hebrew (modern)
        allLocales.add(new Locale("it", "IT")); // Italy / lang=Italian
        allLocales.add(new Locale("en", "JM")); // Jamaica / lang=English
        allLocales.add(new Locale("ja", "JP")); // Japan / lang=Japanese
        allLocales.add(new Locale("en", "JE")); // Jersey / lang=English
        allLocales.add(new Locale("ar", "JO")); // Jordan / lang=Arabic
        allLocales.add(new Locale("kk", "KZ")); // Kazakhstan / lang=Kazakh
        allLocales.add(new Locale("en", "KE")); // Kenya / lang=English
        allLocales.add(new Locale("en", "KI")); // Kiribati / lang=English
        allLocales.add(new Locale("ar", "KW")); // Kuwait / lang=Arabic
        allLocales.add(new Locale("ky", "KG")); // Kyrgyzstan / lang=Kyrgyz
        allLocales.add(new Locale("lo", "LA")); // Lao People's Democratic Republic / lang=Lao
        allLocales.add(new Locale("lv", "LV")); // Latvia / lang=Latvian
        allLocales.add(new Locale("ar", "LB")); // Lebanon / lang=Arabic
        allLocales.add(new Locale("en", "LS")); // Lesotho / lang=English
        allLocales.add(new Locale("en", "LR")); // Liberia / lang=English
        allLocales.add(new Locale("ar", "LY")); // Libya / lang=Arabic
        allLocales.add(new Locale("de", "LI")); // Liechtenstein / lang=German
        allLocales.add(new Locale("lt", "LT")); // Lithuania / lang=Lithuanian
        allLocales.add(new Locale("fr", "LU")); // Luxembourg / lang=French
        allLocales.add(new Locale("zh", "MO")); // Macao / lang=Chinese
        allLocales.add(new Locale("mk", "MK")); // Macedonia (the former Yugoslav Republic of) / lang=Macedonian
        allLocales.add(new Locale("fr", "MG")); // Madagascar / lang=French
        allLocales.add(new Locale("en", "MW")); // Malawi / lang=English
        allLocales.add(new Locale("en", "MY")); // Malaysia / lang=Malaysian
        allLocales.add(new Locale("dv", "MV")); // Maldives / lang=Divehi
        allLocales.add(new Locale("fr", "ML")); // Mali / lang=French
        allLocales.add(new Locale("mt", "MT")); // Malta / lang=Maltese
        allLocales.add(new Locale("en", "MH")); // Marshall Islands / lang=English
        allLocales.add(new Locale("fr", "MQ")); // Martinique / lang=French
        allLocales.add(new Locale("ar", "MR")); // Mauritania / lang=Arabic
        allLocales.add(new Locale("en", "MU")); // Mauritius / lang=English
        allLocales.add(new Locale("fr", "YT")); // Mayotte / lang=French
        allLocales.add(new Locale("es", "MX")); // Mexico / lang=Spanish
        allLocales.add(new Locale("en", "FM")); // Micronesia (Federated States of) / lang=English
        allLocales.add(new Locale("ro", "MD")); // Moldova (Republic of) / lang=Romanian
        allLocales.add(new Locale("fr", "MC")); // Monaco / lang=French
        allLocales.add(new Locale("mn", "MN")); // Mongolia / lang=Mongolian
        allLocales.add(new Locale("sr", "ME")); // Montenegro / lang=Serbian
        allLocales.add(new Locale("en", "MS")); // Montserrat / lang=English
        allLocales.add(new Locale("ar", "MA")); // Morocco / lang=Arabic
        allLocales.add(new Locale("pt", "MZ")); // Mozambique / lang=Portuguese
        allLocales.add(new Locale("my", "MM")); // Myanmar / lang=Burmese
        allLocales.add(new Locale("en", "NA")); // Namibia / lang=English
        allLocales.add(new Locale("en", "NR")); // Nauru / lang=English
        allLocales.add(new Locale("ne", "NP")); // Nepal / lang=Nepali
        allLocales.add(new Locale("nl", "NL")); // Netherlands / lang=Dutch
        allLocales.add(new Locale("fr", "NC")); // New Caledonia / lang=French
        allLocales.add(new Locale("en", "NZ")); // New Zealand / lang=English
        allLocales.add(new Locale("es", "NI")); // Nicaragua / lang=Spanish
        allLocales.add(new Locale("fr", "NE")); // Niger / lang=French
        allLocales.add(new Locale("en", "NG")); // Nigeria / lang=English
        allLocales.add(new Locale("en", "NU")); // Niue / lang=English
        allLocales.add(new Locale("en", "NF")); // Norfolk Island / lang=English
        allLocales.add(new Locale("ko", "KP")); // Korea (Democratic People's Republic of) / lang=Korean
        allLocales.add(new Locale("en", "MP")); // Northern Mariana Islands / lang=English
        allLocales.add(new Locale("no", "NO")); // Norway / lang=Norwegian
        allLocales.add(new Locale("ar", "OM")); // Oman / lang=Arabic
        allLocales.add(new Locale("en", "PK")); // Pakistan / lang=English
        allLocales.add(new Locale("en", "PW")); // Palau / lang=English
        allLocales.add(new Locale("ar", "PS")); // Palestine, State of / lang=Arabic
        allLocales.add(new Locale("es", "PA")); // Panama / lang=Spanish
        allLocales.add(new Locale("en", "PG")); // Papua New Guinea / lang=English
        allLocales.add(new Locale("es", "PY")); // Paraguay / lang=Spanish
        allLocales.add(new Locale("es", "PE")); // Peru / lang=Spanish
        allLocales.add(new Locale("en", "PH")); // Philippines / lang=English
        allLocales.add(new Locale("en", "PN")); // Pitcairn / lang=English
        allLocales.add(new Locale("pl", "PL")); // Poland / lang=Polish
        allLocales.add(new Locale("pt", "PT")); // Portugal / lang=Portuguese
        allLocales.add(new Locale("es", "PR")); // Puerto Rico / lang=Spanish
        allLocales.add(new Locale("ar", "QA")); // Qatar / lang=Arabic
        allLocales.add(new Locale("sq", "XK")); // Republic of Kosovo / lang=Albanian
        allLocales.add(new Locale("fr", "RE")); // Réunion / lang=French
        allLocales.add(new Locale("ro", "RO")); // Romania / lang=Romanian
        allLocales.add(new Locale("ru", "RU")); // Russian Federation / lang=Russian
        allLocales.add(new Locale("rw", "RW")); // Rwanda / lang=Kinyarwanda
        allLocales.add(new Locale("fr", "BL")); // Saint Barthélemy / lang=French
        allLocales.add(new Locale("en", "SH")); // Saint Helena, Ascension and Tristan da Cunha / lang=English
        allLocales.add(new Locale("en", "KN")); // Saint Kitts and Nevis / lang=English
        allLocales.add(new Locale("en", "LC")); // Saint Lucia / lang=English
        allLocales.add(new Locale("en", "MF")); // Saint Martin (French part) / lang=English
        allLocales.add(new Locale("fr", "PM")); // Saint Pierre and Miquelon / lang=French
        allLocales.add(new Locale("en", "VC")); // Saint Vincent and the Grenadines / lang=English
        allLocales.add(new Locale("sm", "WS")); // Samoa / lang=Samoan
        allLocales.add(new Locale("it", "SM")); // San Marino / lang=Italian
        allLocales.add(new Locale("pt", "ST")); // Sao Tome and Principe / lang=Portuguese
        allLocales.add(new Locale("ar", "SA")); // Saudi Arabia / lang=Arabic
        allLocales.add(new Locale("fr", "SN")); // Senegal / lang=French
        allLocales.add(new Locale("sr", "RS")); // Serbia / lang=Serbian
        allLocales.add(new Locale("fr", "SC")); // Seychelles / lang=French
        allLocales.add(new Locale("en", "SL")); // Sierra Leone / lang=English
        allLocales.add(new Locale("en", "SG")); // Singapore / lang=English
        allLocales.add(new Locale("nl", "SX")); // Sint Maarten (Dutch part) / lang=Dutch
        allLocales.add(new Locale("sk", "SK")); // Slovakia / lang=Slovak
        allLocales.add(new Locale("sl", "SI")); // Slovenia / lang=Slovene
        allLocales.add(new Locale("en", "SB")); // Solomon Islands / lang=English
        allLocales.add(new Locale("so", "SO")); // Somalia / lang=Somali
        allLocales.add(new Locale("af", "ZA")); // South Africa / lang=Afrikaans
        allLocales.add(new Locale("en", "GS")); // South Georgia and the South Sandwich Islands / lang=English
        allLocales.add(new Locale("ko", "KR")); // Korea (Republic of) / lang=Korean
        allLocales.add(new Locale("en", "SS")); // South Sudan / lang=English
        allLocales.add(new Locale("es", "ES")); // Spain / lang=Spanish
        allLocales.add(new Locale("si", "LK")); // Sri Lanka / lang=Sinhalese
        allLocales.add(new Locale("ar", "SD")); // Sudan / lang=Arabic
        allLocales.add(new Locale("nl", "SR")); // Suriname / lang=Dutch
        allLocales.add(new Locale("no", "SJ")); // Svalbard and Jan Mayen / lang=Norwegian
        allLocales.add(new Locale("en", "SZ")); // Swaziland / lang=English
        allLocales.add(new Locale("sv", "SE")); // Sweden / lang=Swedish
        allLocales.add(new Locale("de", "CH")); // Switzerland / lang=German
        allLocales.add(new Locale("ar", "SY")); // Syrian Arab Republic / lang=Arabic
        allLocales.add(new Locale("zh", "TW")); // Taiwan / lang=Chinese
        allLocales.add(new Locale("tg", "TJ")); // Tajikistan / lang=Tajik
        allLocales.add(new Locale("sw", "TZ")); // Tanzania, United Republic of / lang=Swahili
        allLocales.add(new Locale("th", "TH")); // Thailand / lang=Thai
        allLocales.add(new Locale("pt", "TL")); // Timor-Leste / lang=Portuguese
        allLocales.add(new Locale("fr", "TG")); // Togo / lang=French
        allLocales.add(new Locale("en", "TK")); // Tokelau / lang=English
        allLocales.add(new Locale("en", "TO")); // Tonga / lang=English
        allLocales.add(new Locale("en", "TT")); // Trinidad and Tobago / lang=English
        allLocales.add(new Locale("ar", "TN")); // Tunisia / lang=Arabic
        allLocales.add(new Locale("tr", "TR")); // Turkey / lang=Turkish
        allLocales.add(new Locale("tk", "TM")); // Turkmenistan / lang=Turkmen
        allLocales.add(new Locale("en", "TC")); // Turks and Caicos Islands / lang=English
        allLocales.add(new Locale("en", "TV")); // Tuvalu / lang=English
        allLocales.add(new Locale("en", "UG")); // Uganda / lang=English
        allLocales.add(new Locale("uk", "UA")); // Ukraine / lang=Ukrainian
        allLocales.add(new Locale("ar", "AE")); // United Arab Emirates / lang=Arabic
        allLocales.add(new Locale("en", "GB")); // United Kingdom of Great Britain and Northern Ireland / lang=English
        allLocales.add(new Locale("en", "US")); // United States of America / lang=English
        allLocales.add(new Locale("es", "UY")); // Uruguay / lang=Spanish
        allLocales.add(new Locale("uz", "UZ")); // Uzbekistan / lang=Uzbek
        allLocales.add(new Locale("bi", "VU")); // Vanuatu / lang=Bislama
        allLocales.add(new Locale("es", "VE")); // Venezuela (Bolivarian Republic of) / lang=Spanish
        allLocales.add(new Locale("vi", "VN")); // Viet Nam / lang=Vietnamese
        allLocales.add(new Locale("fr", "WF")); // Wallis and Futuna / lang=French
        allLocales.add(new Locale("es", "EH")); // Western Sahara / lang=Spanish
        allLocales.add(new Locale("ar", "YE")); // Yemen / lang=Arabic
        allLocales.add(new Locale("en", "ZM")); // Zambia / lang=English
        allLocales.add(new Locale("en", "ZW")); // Zimbabwe / lang=English

        return allLocales;
    }
}
