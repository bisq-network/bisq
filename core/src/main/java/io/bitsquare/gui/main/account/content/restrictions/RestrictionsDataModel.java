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

package io.bitsquare.gui.main.account.content.restrictions;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorService;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.Region;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

class RestrictionsDataModel implements Activatable, DataModel {

    private final User user;
    private final AccountSettings accountSettings;
    private final Persistence persistence;
    private final ArbitratorService messageService;

    final ObservableList<Locale> languageList = FXCollections.observableArrayList();
    final ObservableList<Country> countryList = FXCollections.observableArrayList();
    final ObservableList<Arbitrator> arbitratorList = FXCollections.observableArrayList();
    final ObservableList<Locale> allLanguages = FXCollections.observableArrayList(LanguageUtil
            .getAllLanguageLocales());
    final ObservableList<Region> allRegions = FXCollections.observableArrayList(CountryUtil.getAllRegions());


    @Inject
    public RestrictionsDataModel(User user, AccountSettings accountSettings, Persistence persistence,
                                 ArbitratorService messageService) {
        this.user = user;
        this.accountSettings = accountSettings;
        this.persistence = persistence;
        this.messageService = messageService;
    }

    @Override
    public void activate() {
        countryList.setAll(accountSettings.getAcceptedCountries());
        languageList.setAll(accountSettings.getAcceptedLanguageLocales());
        arbitratorList.setAll(accountSettings.getAcceptedArbitrators());
    }

    @Override
    public void deactivate() {
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion));
    }

    void updateArbitratorList() {
        arbitratorList.setAll(accountSettings.getAcceptedArbitrators());
    }

    void addLanguage(Locale locale) {
        if (locale != null && !languageList.contains(locale)) {
            languageList.add(locale);
            accountSettings.addAcceptedLanguageLocale(locale);
        }
    }

    void removeLanguage(Locale locale) {
        languageList.remove(locale);
        accountSettings.removeAcceptedLanguageLocale(locale);
    }

    void addCountry(Country country) {
        if (!countryList.contains(country) && country != null) {
            countryList.add(country);
            accountSettings.addAcceptedCountry(country);
        }
    }

    ObservableList<Country> getListWithAllEuroCountries() {
        // TODO use Set instead of List
        // In addAcceptedCountry there is a check to no add duplicates, so it works correctly for now
        CountryUtil.getAllEuroCountries().stream().forEach(accountSettings::addAcceptedCountry);
        countryList.setAll(accountSettings.getAcceptedCountries());
        return countryList;
    }

    void removeCountry(Country country) {
        countryList.remove(country);
        accountSettings.removeAcceptedCountry(country);
    }

    void removeArbitrator(Arbitrator arbitrator) {
        arbitratorList.remove(arbitrator);
        accountSettings.removeAcceptedArbitrator(arbitrator);
    }
}
