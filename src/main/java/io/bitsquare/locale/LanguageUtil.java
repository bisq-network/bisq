package io.bitsquare.locale;

import java.util.*;

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

    public static List<Locale> getAllLanguageLocales()
    {
        List<Locale> allLocales = Arrays.asList(Locale.getAvailableLocales());
        Set<Locale> allLocalesAsSet = new HashSet<>();
        for (int i = 0; i < allLocales.size(); i++)
        {
            Locale locale = allLocales.get(i);
            if (!locale.getLanguage().equals(""))
            {
                allLocalesAsSet.add(new Locale(locale.getLanguage(), ""));
            }
        }
        allLocales = new ArrayList<>();
        allLocales.addAll(allLocalesAsSet);
        allLocales.sort(new Comparator<Locale>()
        {
            @Override
            public int compare(Locale locale1, Locale locale2)
            {
                return locale1.getDisplayLanguage().compareTo(locale2.getDisplayLanguage());
            }
        });

        return allLocales;
    }

    public static Locale getDefaultLanguageLocale()
    {
        return new Locale(Locale.getDefault().getLanguage(), "");
    }
}
