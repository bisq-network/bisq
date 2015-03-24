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

package io.bitsquare.user;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.persistence.Storage;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

import javax.inject.Inject;

public class AccountSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    transient private Storage<AccountSettings> storage;

    // Persisted fields
    private List<Locale> acceptedLanguageLocales = new ArrayList<>();
    private List<Country> acceptedCountryLocales = new ArrayList<>();
    private List<Arbitrator> acceptedArbitrators = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountSettings(Storage<AccountSettings> storage) {
        this.storage = storage;

        AccountSettings persisted = storage.getPersisted(this);
        if (persisted != null) {
            acceptedLanguageLocales = persisted.getAcceptedLanguageLocales();
            acceptedCountryLocales = persisted.getAcceptedCountries();
            acceptedArbitrators = persisted.getAcceptedArbitrators();
        }
        else {
            acceptedLanguageLocales = Arrays.asList(LanguageUtil.getDefaultLanguageLocale(), LanguageUtil.getEnglishLanguageLocale());
            acceptedCountryLocales = Arrays.asList(CountryUtil.getDefaultCountry());
            acceptedArbitrators = new ArrayList<>();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void addAcceptedLanguageLocale(Locale locale) {
        if (!acceptedLanguageLocales.contains(locale)) {
            acceptedLanguageLocales.add(locale);
            storage.save();
        }
    }

    public void removeAcceptedLanguageLocale(Locale item) {
        acceptedLanguageLocales.remove(item);
    }

    public void addAcceptedCountry(Country locale) {
        if (!acceptedCountryLocales.contains(locale)) {
            acceptedCountryLocales.add(locale);
            storage.save();
        }
    }

    public void removeAcceptedCountry(Country item) {
        acceptedCountryLocales.remove(item);
    }

    public void addAcceptedArbitrator(Arbitrator arbitrator) {
        if (!acceptedArbitrators.contains(arbitrator)) {
            acceptedArbitrators.add(arbitrator);
            storage.save();
        }
    }

    public void removeAcceptedArbitrator(Arbitrator item) {
        acceptedArbitrators.remove(item);
        storage.save();
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

    public Coin getSecurityDeposit() {
        OptionalLong result = acceptedArbitrators.stream().mapToLong(e -> e.getFee().getValue()).max();
        return result.isPresent() ? Coin.valueOf(result.getAsLong()) : Coin.ZERO;
    }

}
