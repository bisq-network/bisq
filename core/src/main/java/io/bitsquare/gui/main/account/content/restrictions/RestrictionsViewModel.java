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
import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import com.google.inject.Inject;

import java.util.Locale;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;

class RestrictionsViewModel extends ActivatableWithDataModel<RestrictionsDataModel> implements ViewModel {

    final BooleanProperty doneButtonDisable = new SimpleBooleanProperty(true);


    @Inject
    public RestrictionsViewModel(RestrictionsDataModel dataModel) {
        super(dataModel);
    }


    @Override
    public void doActivate() {
        updateDoneButtonDisableState();
    }

    void addLanguage(Locale locale) {
        dataModel.addLanguage(locale);
        updateDoneButtonDisableState();
    }

    void removeLanguage(Locale locale) {
        dataModel.removeLanguage(locale);
        updateDoneButtonDisableState();
    }

    void addCountry(Country country) {
        dataModel.addCountry(country);
        updateDoneButtonDisableState();
    }

    void removeCountry(Country country) {
        dataModel.removeCountry(country);
        updateDoneButtonDisableState();
    }

    void removeArbitrator(Arbitrator arbitrator) {
        dataModel.removeArbitrator(arbitrator);
        updateDoneButtonDisableState();
    }

    void updateArbitratorList() {
        dataModel.updateArbitratorList();
        updateDoneButtonDisableState();
    }


    ObservableList<Country> getListWithAllEuroCountries() {
        return dataModel.getListWithAllEuroCountries();
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return dataModel.getAllCountriesFor(selectedRegion);
    }

    ObservableList<Locale> getLanguageList() {
        return dataModel.languageList;
    }

    ObservableList<Region> getAllRegions() {
        return dataModel.allRegions;
    }

    ObservableList<Locale> getAllLanguages() {
        return dataModel.allLanguages;
    }

    ObservableList<Country> getCountryList() {
        return dataModel.countryList;
    }

    ObservableList<Arbitrator> getArbitratorList() {
        return dataModel.arbitratorList;
    }


    //TODO Revert size() > -1 to 0(2 later). For mock testing disabled arbitratorList test
    private void updateDoneButtonDisableState() {
        boolean isValid = dataModel.languageList != null && dataModel.languageList.size() > 0 &&
                dataModel.countryList != null && dataModel.countryList.size() > 0 &&
                dataModel.arbitratorList != null && dataModel.arbitratorList.size() > -1;
        doneButtonDisable.set(!isValid);
    }

}
