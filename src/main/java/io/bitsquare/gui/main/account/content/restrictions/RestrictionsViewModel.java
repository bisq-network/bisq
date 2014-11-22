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

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.gui.ActivatableWithDelegate;
import io.bitsquare.gui.ViewModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import com.google.inject.Inject;

import java.util.Locale;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;

class RestrictionsViewModel extends ActivatableWithDelegate<RestrictionsModel> implements ViewModel {

    final BooleanProperty doneButtonDisable = new SimpleBooleanProperty(true);


    @Inject
    public RestrictionsViewModel(RestrictionsModel model) {
        super(model);
    }


    @Override
    public void doActivate() {
        updateDoneButtonDisableState();
    }

    void addLanguage(Locale locale) {
        delegate.addLanguage(locale);
        updateDoneButtonDisableState();
    }

    void removeLanguage(Locale locale) {
        delegate.removeLanguage(locale);
        updateDoneButtonDisableState();
    }

    void addCountry(Country country) {
        delegate.addCountry(country);
        updateDoneButtonDisableState();
    }

    void removeCountry(Country country) {
        delegate.removeCountry(country);
        updateDoneButtonDisableState();
    }

    void removeArbitrator(Arbitrator arbitrator) {
        delegate.removeArbitrator(arbitrator);
        updateDoneButtonDisableState();
    }

    void updateArbitratorList() {
        delegate.updateArbitratorList();
        updateDoneButtonDisableState();
    }


    ObservableList<Country> getListWithAllEuroCountries() {
        return delegate.getListWithAllEuroCountries();
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return delegate.getAllCountriesFor(selectedRegion);
    }

    ObservableList<Locale> getLanguageList() {
        return delegate.languageList;
    }

    ObservableList<Region> getAllRegions() {
        return delegate.allRegions;
    }

    ObservableList<Locale> getAllLanguages() {
        return delegate.allLanguages;
    }

    ObservableList<Country> getCountryList() {
        return delegate.countryList;
    }

    ObservableList<Arbitrator> getArbitratorList() {
        return delegate.arbitratorList;
    }


    //TODO Revert size() > -1 to 0(2 later). For mock testing disabled arbitratorList test
    private void updateDoneButtonDisableState() {
        boolean isValid = delegate.languageList != null && delegate.languageList.size() > 0 &&
                delegate.countryList != null && delegate.countryList.size() > 0 &&
                delegate.arbitratorList != null && delegate.arbitratorList.size() > -1;
        doneButtonDisable.set(!isValid);
    }

}
