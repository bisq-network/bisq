package io.bitsquare.settings;

import io.bitsquare.locale.Country;
import io.bitsquare.user.Arbitrator;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;

public class Settings implements Serializable
{
    private static final long serialVersionUID = 7995048077355006861L;


    private List<Locale> acceptedLanguageLocales = new ArrayList<>();

    private List<Country> acceptedCountryLocales = new ArrayList<>();

    private List<Arbitrator> acceptedArbitrators = new ArrayList<>();
    private int maxCollateral;
    private int minCollateral;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Settings()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyPersistedSettings(Settings persistedSettings)
    {
        if (persistedSettings != null)
        {
            acceptedLanguageLocales = persistedSettings.getAcceptedLanguageLocales();
            acceptedCountryLocales = persistedSettings.getAcceptedCountries();
            acceptedArbitrators = persistedSettings.getAcceptedArbitrators();
            maxCollateral = persistedSettings.getMaxCollateral();
            minCollateral = persistedSettings.getMinCollateral();
        }
    }

    public void addAcceptedLanguageLocale(Locale locale)
    {
        if (!acceptedLanguageLocales.contains(locale))
        {
            acceptedLanguageLocales.add(locale);
        }
    }

    public void removeAcceptedLanguageLocale(Locale item)
    {
        acceptedLanguageLocales.remove(item);
    }

    public void addAcceptedCountry(Country locale)
    {
        if (!acceptedCountryLocales.contains(locale))
        {
            acceptedCountryLocales.add(locale);
        }
    }

    public void removeAcceptedCountry(Country item)
    {
        acceptedCountryLocales.remove(item);
    }

    public void addAcceptedArbitrator(Arbitrator arbitrator)
    {
        if (!acceptedArbitrators.contains(arbitrator))
        {
            acceptedArbitrators.add(arbitrator);
        }
    }

    public void removeAcceptedArbitrator(Arbitrator item)
    {
        acceptedArbitrators.remove(item);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public List<Arbitrator> getAcceptedArbitrators()
    {
        return acceptedArbitrators;
    }


    public List<Locale> getAcceptedLanguageLocales()
    {
        return acceptedLanguageLocales;
    }


    public List<Country> getAcceptedCountries()
    {
        return acceptedCountryLocales;
    }

    //TODO
    @SuppressWarnings("UnusedParameters")

    public Arbitrator getRandomArbitrator(@SuppressWarnings("UnusedParameters") Integer collateral, @SuppressWarnings("UnusedParameters") BigInteger amount)
    {
        List<Arbitrator> candidates = new ArrayList<>();
        //noinspection Convert2streamapi
        for (Arbitrator arbitrator : acceptedArbitrators)
        {
            /*if (arbitrator.getArbitrationFeePercent() >= collateral &&
                    arbitrator.getMinArbitrationAmount().compareTo(amount) < 0)
            {   */
            candidates.add(arbitrator);
            // }
        }
        return !candidates.isEmpty() ? candidates.get((int) (Math.random() * candidates.size())) : null;
    }

    int getMaxCollateral()
    {
        return maxCollateral;
    }

    public void setMaxCollateral(int maxCollateral)
    {
        this.maxCollateral = maxCollateral;
    }

    public int getMinCollateral()
    {
        return minCollateral;
    }

    public void setMinCollateral(int minCollateral)
    {
        this.minCollateral = minCollateral;
    }

}
