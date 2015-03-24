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

    public static List<Locale> getAllLanguageLocales() {
        List<Locale> allLocales = Arrays.asList(Locale.getAvailableLocales());
        final Set<Locale> allLocalesAsSet =
                allLocales.stream().filter(locale -> !"".equals(locale.getLanguage())).map(locale ->
                        new Locale(locale.getLanguage(), "")).collect(Collectors.toSet());
        allLocales = new ArrayList<>();
        allLocales.addAll(allLocalesAsSet);
        allLocales.sort((locale1, locale2) -> locale1.getDisplayLanguage().compareTo(locale2.getDisplayLanguage()));
        return allLocales;
    }

    public static Locale getDefaultLanguageLocale() {
        if (Locale.getDefault() != null)
            return new Locale(Locale.getDefault().getLanguage(), "");
        else
            return getEnglishLanguageLocale();
    }

    public static Locale getEnglishLanguageLocale() {
        return new Locale(Locale.ENGLISH.getLanguage(), "");
    }
}
