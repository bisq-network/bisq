package io.bitsquare.settings;

import com.google.inject.Inject;
import io.bitsquare.user.Arbitrator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Settings implements Serializable
{
    private static final long serialVersionUID = 7995048077355006861L;

    private List<Locale> acceptedLanguageLocales = new ArrayList<>();
    private List<Locale> acceptedCountryLocales = new ArrayList<>();
    private List<Arbitrator> arbitrators = new ArrayList<>();

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
            arbitrators = savedSettings.getArbitrators();
        }
    }

    public void addAcceptedLanguageLocale(Locale locale)
    {
        if (!acceptedLanguageLocales.contains(locale))
            acceptedLanguageLocales.add(locale);
    }

    public void addAcceptedCountryLocale(Locale locale)
    {
        if (!acceptedCountryLocales.contains(locale))
            acceptedCountryLocales.add(locale);
    }

    public void addArbitrator(Arbitrator arbitrator)
    {
        if (!arbitrators.contains(arbitrator))
            arbitrators.add(arbitrator);
    }

    //getters
    public List<Arbitrator> getArbitrators()
    {
        return arbitrators;
    }

    public List<Locale> getAcceptedLanguageLocales()
    {
        return acceptedLanguageLocales;
    }


    public List<Locale> getAcceptedCountryLocales()
    {
        return acceptedCountryLocales;
    }

    public Arbitrator getRandomArbitrator()
    {
        return arbitrators.size() > 0 ? arbitrators.get((int) (Math.random() * arbitrators.size())) : null;
    }
}
