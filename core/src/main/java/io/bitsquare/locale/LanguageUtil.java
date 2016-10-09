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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class LanguageUtil {
    private static final Logger log = LoggerFactory.getLogger(LanguageUtil.class);

    public static List<String> getAllLanguageCodes() {
        List<Locale> allLocales = LocaleUtil.getAllLocales();

        final Set<String> allLocaleCodesAsSet = allLocales.stream()
                .filter(locale -> !"".equals(locale.getLanguage()) && !"".equals(locale.getDisplayLanguage()))
                .map(locale -> new Locale(locale.getLanguage(), "").getLanguage())
                .collect(Collectors.toSet());

        List<String> allLocaleCodes = new ArrayList<>();
        allLocaleCodes.addAll(allLocaleCodesAsSet);
        allLocaleCodes.sort((o1, o2) -> getDisplayName(o1).compareTo(getDisplayName(o2)));
        return allLocaleCodes;
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
}
