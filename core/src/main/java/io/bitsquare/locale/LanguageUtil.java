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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class LanguageUtil {

    public static List<String> getAllLanguageLocaleCodes() {
        List<Locale> allLocales = Arrays.asList(Locale.getAvailableLocales());
        final Set<String> allLocaleCodesAsSet =
                allLocales.stream().filter(locale -> !"".equals(locale.getLanguage())).map(locale ->
                        new Locale(locale.getLanguage(), "").getISO3Language()).collect(Collectors.toSet());
        List<String> allLocaleCodes = new ArrayList<>();
        allLocaleCodes.addAll(allLocaleCodesAsSet);
        allLocaleCodes.sort(String::compareTo);
        return allLocaleCodes;
    }

    public static String getDefaultLanguageLocaleAsCode() {
        if (Locale.getDefault() != null)
            return new Locale(Locale.getDefault().getLanguage(), "").getISO3Language();
        else
            return getEnglishLanguageLocaleCode();
    }

    public static String getEnglishLanguageLocaleCode() {
        return new Locale(Locale.ENGLISH.getLanguage(), "").getISO3Language();
    }

    public static String getDisplayName(String code) {
        return new Locale(code).getDisplayName();
    }
}
