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
import io.bitsquare.common.model.Activatable;
import io.bitsquare.common.model.DataModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.Region;
import io.bitsquare.user.AccountSettings;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

class RestrictionsDataModel implements Activatable, DataModel {

    private final AccountSettings accountSettings;

    final ObservableList<String> languageCodes = FXCollections.observableArrayList();
    final ObservableList<Country> countries = FXCollections.observableArrayList();
    final ObservableList<Arbitrator> arbitrators = FXCollections.observableArrayList();
    final ObservableList<String> allLanguageCodes = FXCollections.observableArrayList(LanguageUtil
            .getAllLanguageLocaleCodes());
    final ObservableList<Region> allRegions = FXCollections.observableArrayList(CountryUtil.getAllRegions());


    @Inject
    public RestrictionsDataModel(AccountSettings accountSettings) {
        this.accountSettings = accountSettings;
    }

    @Override
    public void activate() {
        countries.setAll(accountSettings.getAcceptedCountries());
        languageCodes.setAll(accountSettings.getAcceptedLanguageLocaleCodes());
        arbitrators.setAll(accountSettings.getAcceptedArbitrators());
    }

    @Override
    public void deactivate() {
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion));
    }

    void updateArbitratorList() {
        arbitrators.setAll(accountSettings.getAcceptedArbitrators());
    }

    void addLanguageCode(String code) {
        if (code != null && !languageCodes.contains(code)) {
            languageCodes.add(code);
            accountSettings.addAcceptedLanguageLocale(code);
        }
    }

    void removeLanguage(String code) {
        languageCodes.remove(code);
        accountSettings.removeAcceptedLanguageLocale(code);
    }

    void addCountry(Country country) {
        if (!countries.contains(country) && country != null) {
            countries.add(country);
            accountSettings.addAcceptedCountry(country);
        }
    }

    ObservableList<Country> getListWithAllEuroCountries() {
        // TODO use Set instead of List
        // In addAcceptedCountry there is a check to no add duplicates, so it works correctly for now
        CountryUtil.getAllEuroCountries().stream().forEach(accountSettings::addAcceptedCountry);
        countries.setAll(accountSettings.getAcceptedCountries());
        return countries;
    }

    void removeCountry(Country country) {
        countries.remove(country);
        accountSettings.removeAcceptedCountry(country);
    }

    void removeArbitrator(Arbitrator arbitrator) {
        arbitrators.remove(arbitrator);
        accountSettings.removeAcceptedArbitrator(arbitrator);
    }
}
