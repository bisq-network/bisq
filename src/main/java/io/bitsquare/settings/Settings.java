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

package io.bitsquare.settings;

import io.bitsquare.locale.Country;
import io.bitsquare.user.Arbitrator;

import com.google.bitcoin.core.Coin;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Settings implements Serializable {
    private static final long serialVersionUID = 7995048077355006861L;


    private List<Locale> acceptedLanguageLocales = new ArrayList<>();
    private List<Country> acceptedCountryLocales = new ArrayList<>();
    private List<Arbitrator> acceptedArbitrators = new ArrayList<>();
    private double collateral = 0.01;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Settings() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyPersistedSettings(Settings persistedSettings) {
        if (persistedSettings != null) {
            acceptedLanguageLocales = persistedSettings.getAcceptedLanguageLocales();
            acceptedCountryLocales = persistedSettings.getAcceptedCountries();
            acceptedArbitrators = persistedSettings.getAcceptedArbitrators();
            collateral = persistedSettings.getCollateral();
        }
    }

    public void addAcceptedLanguageLocale(Locale locale) {
        if (!acceptedLanguageLocales.contains(locale)) {
            acceptedLanguageLocales.add(locale);
        }
    }

    public void removeAcceptedLanguageLocale(Locale item) {
        acceptedLanguageLocales.remove(item);
    }

    public void addAcceptedCountry(Country locale) {
        if (!acceptedCountryLocales.contains(locale)) {
            acceptedCountryLocales.add(locale);
        }
    }

    public void removeAcceptedCountry(Country item) {
        acceptedCountryLocales.remove(item);
    }

    public void addAcceptedArbitrator(Arbitrator arbitrator) {
        if (!acceptedArbitrators.contains(arbitrator)) {
            acceptedArbitrators.add(arbitrator);
        }
    }

    public void removeAcceptedArbitrator(Arbitrator item) {
        acceptedArbitrators.remove(item);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public List<Arbitrator> getAcceptedArbitrators() {
        return acceptedArbitrators;
    }


    public List<Locale> getAcceptedLanguageLocales() {
        return acceptedLanguageLocales;
    }


    public List<Country> getAcceptedCountries() {
        return acceptedCountryLocales;
    }

    //TODO
    public Arbitrator getRandomArbitrator(Coin amount) {
        List<Arbitrator> candidates = new ArrayList<>();
        //noinspection Convert2streamapi
        for (Arbitrator arbitrator : acceptedArbitrators) {
            /*if (arbitrator.getArbitrationFeePercent() >= collateral &&
                    arbitrator.getMinArbitrationAmount().compareTo(amount) < 0)
            {   */
            candidates.add(arbitrator);
            // }
        }
        return !candidates.isEmpty() ? candidates.get((int) (Math.random() * candidates.size())) : null;
    }


    public double getCollateral() {
        return collateral;
    }

}
