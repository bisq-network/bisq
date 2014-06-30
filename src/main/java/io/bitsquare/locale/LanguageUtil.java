package io.bitsquare.locale;

import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class LanguageUtil
{

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

    @NotNull
    public static List<Locale> getAllLanguageLocales()
    {
        @NotNull List<Locale> allLocales = Arrays.asList(Locale.getAvailableLocales());
        final Set<Locale> allLocalesAsSet = allLocales.stream().filter(locale -> !"".equals(locale.getLanguage())).map(locale -> new Locale(locale.getLanguage(), "")).collect(Collectors.toSet());
        allLocales = new ArrayList<>();
        allLocales.addAll(allLocalesAsSet);
        allLocales.sort((locale1, locale2) -> locale1.getDisplayLanguage().compareTo(locale2.getDisplayLanguage()));
        return allLocales;
    }

    @NotNull
    public static Locale getDefaultLanguageLocale()
    {
        return new Locale(Locale.getDefault().getLanguage(), "");
    }
}
