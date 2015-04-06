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
import io.bitsquare.common.model.ActivatableWithDataModel;
import io.bitsquare.common.model.ViewModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import com.google.inject.Inject;

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

    void addLanguage(String locale) {
        dataModel.addLanguageCode(locale);
        updateDoneButtonDisableState();
    }

    void removeLanguage(String locale) {
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

    ObservableList<String> getLanguageCodes() {
        return dataModel.languageCodes;
    }

    ObservableList<Region> getAllRegions() {
        return dataModel.allRegions;
    }

    ObservableList<String> getAllLanguageCodes() {
        return dataModel.allLanguageCodes;
    }

    ObservableList<Country> getCountryList() {
        return dataModel.countries;
    }

    ObservableList<Arbitrator> getArbitratorList() {
        return dataModel.arbitrators;
    }


    //TODO Revert size() > -1 to 0(2 later). For mock testing disabled arbitratorList test
    private void updateDoneButtonDisableState() {
        boolean isValid = dataModel.languageCodes != null && dataModel.languageCodes.size() > 0 &&
                dataModel.countries != null && dataModel.countries.size() > 0 &&
                dataModel.arbitrators != null && dataModel.arbitrators.size() > -1;
        doneButtonDisable.set(!isValid);
    }

}
