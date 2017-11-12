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
public class LanguageUtil {
    private static final List<String> userLanguageCodes = Arrays.asList(
            "en", // English
            "de", // German
            "el", // Greek
            "es", // Spanish
            "pt", // Portuguese / Brazil
            "sr",  // Serbian
            "zh",  // Chinese
            "hu", // Hungarian
            "ro", // Romanian
            "ru", // Russian
            "fr", // French
            "tr" // Turkish
            
            /*
            // not translated yet
            "it", // Italian
            "ja", // Japanese
            "iw", // Hebrew
            "hi", // Hindi
            "ko", // Korean
            "pl", // Polish
            "sv", // Swedish
            "no", // Norwegian
            "nl", // Dutch
            "be", // Belarusian
            "fi", // Finnish
            "bg", // Bulgarian
            "lt", // Lithuanian
            "lv", // Latvian
            "hr", // Croatian
            "uk", // Ukrainian
            "sk", // Slovak
            "sl", // Slovenian
            "ga", // Irish
            "sq", // Albanian
            "ca", // Catalan
            "mk", // Macedonian
            "kk", // Kazakh
            "km", // Khmer
            "sw", // Swahili
            "in", // Indonesian
            "ms", // Malay
            "is", // Icelandic
            "et", // Estonian
            "cs", // Czech
            "ar", // Arabic
            "vi", // Vietnamese
            "th", // Thai
            "da", // Danish
            "mt"  // Maltese
            */
    );

    public static List<String> getAllLanguageCodes() {
        List<Locale> allLocales = LocaleUtil.getAllLocales();

        // Filter duplicate locale entries 
        Set<String> allLocalesAsSet = allLocales.stream().filter(locale -> !locale.getLanguage().isEmpty() &&
                !locale.getDisplayLanguage().isEmpty())
                .map(Locale::getLanguage)
                .collect(Collectors.toSet());

        List<String> allLanguageCodes = new ArrayList<>();
        allLanguageCodes.addAll(allLocalesAsSet);
        allLanguageCodes.sort((o1, o2) -> getDisplayName(o1).compareTo(getDisplayName(o2)));
        return allLanguageCodes;
    }

    public static String getDefaultLanguage() {
        // might be set later in pref or config, so not use defaultLocale anywhere in the code
        return getLocale().getLanguage();
    }

    public static String getDefaultLanguageLocaleAsCode() {
        return new Locale(LanguageUtil.getDefaultLanguage(), "").getLanguage();
    }

    public static String getEnglishLanguageLocaleCode() {
        return new Locale(Locale.ENGLISH.getLanguage(), "").getLanguage();
    }

    public static String getDisplayName(String code) {
        Locale locale = new Locale(code.toUpperCase());
        if (locale.getLanguage().equals("sr")) {
            // Serbia
            // shows it in russian by default
            return "Srpski";
        } else {
            return locale.getDisplayName(locale);
        }
    }

    public static List<String> getUserLanguageCodes() {
        return userLanguageCodes;
    }

    private static Locale getLocale() {
        return GlobalSettings.getLocale();
    }
}
