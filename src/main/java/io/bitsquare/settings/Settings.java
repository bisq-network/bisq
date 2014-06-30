package io.bitsquare.settings;

import com.google.inject.Inject;
import io.bitsquare.locale.Country;
import io.bitsquare.user.Arbitrator;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Settings implements Serializable
{
    private static final long serialVersionUID = 7995048077355006861L;

    @NotNull
    private List<Locale> acceptedLanguageLocales = new ArrayList<>();
    @NotNull
    private List<Country> acceptedCountryLocales = new ArrayList<>();
    @NotNull
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

    public void updateFromStorage(@Nullable Settings savedSettings)
    {
        if (savedSettings != null)
        {
            acceptedLanguageLocales = savedSettings.getAcceptedLanguageLocales();
            acceptedCountryLocales = savedSettings.getAcceptedCountries();
            acceptedArbitrators = savedSettings.getAcceptedArbitrators();
            maxCollateral = savedSettings.getMaxCollateral();
            minCollateral = savedSettings.getMinCollateral();
        }
    }

    public void addAcceptedLanguageLocale(@NotNull Locale locale)
    {
        if (!acceptedLanguageLocales.contains(locale))
            acceptedLanguageLocales.add(locale);
    }

    public void removeAcceptedLanguageLocale(@NotNull Locale item)
    {
        acceptedLanguageLocales.remove(item);
    }

    public void addAcceptedCountry(@NotNull Country locale)
    {
        if (!acceptedCountryLocales.contains(locale))
            acceptedCountryLocales.add(locale);
    }

    public void removeAcceptedCountry(@NotNull Country item)
    {
        acceptedCountryLocales.remove(item);
    }

    public void addAcceptedArbitrator(@NotNull Arbitrator arbitrator)
    {
        if (!acceptedArbitrators.contains(arbitrator))
            acceptedArbitrators.add(arbitrator);
    }

    public void removeAcceptedArbitrator(@NotNull Arbitrator item)
    {
        acceptedArbitrators.remove(item);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    public List<Arbitrator> getAcceptedArbitrators()
    {
        return acceptedArbitrators;
    }

    @NotNull
    public List<Locale> getAcceptedLanguageLocales()
    {
        return acceptedLanguageLocales;
    }

    @NotNull
    public List<Country> getAcceptedCountries()
    {
        return acceptedCountryLocales;
    }

    //TODO
    @SuppressWarnings("UnusedParameters")
    @Nullable
    public Arbitrator getRandomArbitrator(@SuppressWarnings("UnusedParameters") @NotNull Integer collateral, @SuppressWarnings("UnusedParameters") @NotNull BigInteger amount)
    {
        @NotNull List<Arbitrator> candidates = new ArrayList<>();
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
