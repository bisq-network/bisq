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

import java.util.*;
import java.util.stream.Collectors;

public class LanguageUtil {

    /*public static List<Locale> getPopularLanguages()
    {
        List<Locale> list = new ArrayList<>();
        list.add(new Locale("de", "AT"));
        list.add(new Locale("de", "DE"));
        list.add(new Locale("en", "US"));
        list.add(new Locale("en", "UK"));
        list.add(new Locale("es", "ES"));
        list.add(new Locale("ru", "RU"));
        list.add(new Locale("zh", "CN"));
        list.add(new Locale("en", "AU"));
        list.add(new Locale("it", "IT"));
        list.add(new Locale("en", "CA"));
        return list;
    }  */


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
        return new Locale(Locale.getDefault().getLanguage(), "");
    }

    public static Locale getEnglishLanguageLocale() {
        return new Locale(Locale.ENGLISH.getLanguage(), "");
    }
}
