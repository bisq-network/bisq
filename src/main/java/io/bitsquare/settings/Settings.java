package io.bitsquare.settings;

import com.google.inject.Inject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Settings implements Serializable
{
    private static final long serialVersionUID = 7995048077355006861L;

    private List<Locale> acceptedLanguageLocales = new ArrayList<>();
    private List<Locale> acceptedCountryLocales = new ArrayList<>();

    @Inject
    public Settings()
    {
    }

    public void updateFromStorage(Settings savedSettings)
    {
        if (savedSettings != null)
        {
            acceptedLanguageLocales = savedSettings.getAcceptedLanguageLocales();

            acceptedCountryLocales = savedSettings.getAcceptedCountryLocales();
        }
    }

    public void addAcceptedLanguageLocale(Locale locale)
    {
        acceptedLanguageLocales.add(locale);
    }

    public void addAcceptedCountryLocale(Locale locale)
    {
        acceptedCountryLocales.add(locale);
    }

    //setters
    public void setAcceptedLanguageLocales(List<Locale> acceptedLanguageLocales)
    {
        this.acceptedLanguageLocales = acceptedLanguageLocales;
    }

    public void setAcceptedCountryLocales(List<Locale> acceptedCountryLocales)
    {
        this.acceptedCountryLocales = acceptedCountryLocales;
    }

    //getters
    public List<Locale> getAcceptedLanguageLocales()
    {
        return acceptedLanguageLocales;
    }


    public List<Locale> getAcceptedCountryLocales()
    {
        return acceptedCountryLocales;
    }


}
