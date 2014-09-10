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

    public final BooleanProperty doneButtonDisable = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private RestrictionsPM(RestrictionsModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialized() {
        super.initialized();

    }

    @Override
    public void activate() {
        super.activate();

        updateDoneButtonDisableState();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addLanguage(Locale locale) {
        model.addLanguage(locale);
        updateDoneButtonDisableState();
    }

    public void removeLanguage(Locale locale) {
        model.removeLanguage(locale);
        updateDoneButtonDisableState();
    }

    public void addCountry(Country country) {
        model.addCountry(country);
        updateDoneButtonDisableState();
    }

    public void removeCountry(Country country) {
        model.removeCountry(country);
        updateDoneButtonDisableState();
    }

    public void removeArbitrator(Arbitrator arbitrator) {
        model.removeArbitrator(arbitrator);
        updateDoneButtonDisableState();
    }

    public void updateArbitratorList() {
        model.updateArbitratorList();
        updateDoneButtonDisableState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Country> getListWithAllEuroCountries() {
        return model.getListWithAllEuroCountries();
    }

    public ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return model.getAllCountriesFor(selectedRegion);
    }

    public ObservableList<Locale> getLanguageList() {
        return model.languageList;
    }

    public ObservableList<Region> getAllRegions() {
        return model.allRegions;
    }

    public ObservableList<Locale> getAllLanguages() {
        return model.allLanguages;
    }

    public ObservableList<Country> getCountryList() {
        return model.countryList;
    }

    public ObservableList<Arbitrator> getArbitratorList() {
        return model.arbitratorList;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO Revert size() > -1 to 0(2 later). For mock testing disabled arbitratorList test
    private void updateDoneButtonDisableState() {
        boolean isValid = model.languageList != null && model.languageList.size() > 0 &&
                model.countryList != null && model.countryList.size() > 0 &&
                model.arbitratorList != null && model.arbitratorList.size() > -1;
        doneButtonDisable.set(!isValid);
    }

}
