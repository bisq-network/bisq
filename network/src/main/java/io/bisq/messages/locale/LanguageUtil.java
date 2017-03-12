/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.messages.locale;

import io.bisq.messages.user.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class LanguageUtil {
    private static final Logger log = LoggerFactory.getLogger(LanguageUtil.class);

    private static List<String> userLanguageCodes = Arrays.asList(
            "en", // English
            "de" // German
            
            /*
            // not translated yet
            "es", // Spanish
            "zh", // Chinese
            "pt", // Portuguese
            "it", // Italian
            "el", // Greek
            "fr", // French
            "sr", // Serbian
            "ja", // Japanese
            "iw", // Hebrew
            "hi", // Hindi
            "ru", // Russian
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
            "hu", // Hungarian
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
            "ro", // Romanian
            "tr", // Turkish
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
        // might be set later in pref or config, so not use Preferences.getDefaultLocale() anywhere in the code
        return Preferences.getDefaultLocale().getLanguage();
    }

    public static String getDefaultLanguageLocaleAsCode() {
        return new Locale(LanguageUtil.getDefaultLanguage(), "").getLanguage();
    }

    public static String getEnglishLanguageLocaleCode() {
        return new Locale(Locale.ENGLISH.getLanguage(), "").getLanguage();
    }

    public static String getDisplayName(String code) {
        return new Locale(code.toUpperCase()).getDisplayName(Preferences.getDefaultLocale());
    }

    public static List<String> getUserLanguageCodes() {
        return userLanguageCodes;
    }
}
