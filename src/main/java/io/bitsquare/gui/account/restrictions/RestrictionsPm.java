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

package io.bitsquare.gui.account.restrictions;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import com.google.inject.Inject;

import java.util.Locale;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestrictionsPM extends PresentationModel<RestrictionsModel> {
    private static final Logger log = LoggerFactory.getLogger(RestrictionsPM.class);


    public final BooleanProperty doneButtonDisabled = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public RestrictionsPM(RestrictionsModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialized() {
        super.initialized();

    }

    @Override
    public void activate() {
        super.activate();

        updateDoneButtonDisabled();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onAddLanguage(Locale locale) {
        model.addLanguage(locale);
        updateDoneButtonDisabled();
    }

    ObservableList<Locale> getLanguageList() {
        updateDoneButtonDisabled();
        return model.languageList;
    }

    ObservableList<Locale> getAllLanguages() {
        updateDoneButtonDisabled();
        return model.allLanguages;
    }

    void removeLanguage(Locale locale) {
        model.removeLanguage(locale);
        updateDoneButtonDisabled();
    }

    void onAddCountry(Country country) {
        model.addCountry(country);
        updateDoneButtonDisabled();
    }

    ObservableList<Country> getListWithAllEuroCountries() {
        ObservableList<Country> result = model.getListWithAllEuroCountries();
        updateDoneButtonDisabled();
        return result;
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return model.getAllCountriesFor(selectedRegion);
    }

    ObservableList<Region> getAllRegions() {
        return model.allRegions;
    }

    ObservableList<Country> getCountryList() {
        updateDoneButtonDisabled();
        return model.countryList;
    }

    void removeCountry(Country country) {
        model.removeCountry(country);
        updateDoneButtonDisabled();
    }

    ObservableList<Arbitrator> getArbitratorList() {
        updateDoneButtonDisabled();
        return model.arbitratorList;
    }

    void removeArbitrator(Arbitrator arbitrator) {
        model.removeArbitrator(arbitrator);
        updateDoneButtonDisabled();
    }

    void updateArbitratorList() {
        model.updateArbitratorList();
        updateDoneButtonDisabled();
    }

    //TODO Revert -1 to 0(2 later). For mock testing disabled arbitratorList test
    void updateDoneButtonDisabled() {
        boolean isValid = model.languageList != null && model.languageList.size() > 0 &&
                model.countryList != null && model.countryList.size() > 0 &&
                model.arbitratorList != null && model.arbitratorList.size() > -1;
        doneButtonDisabled.set(!isValid);
    }

}
