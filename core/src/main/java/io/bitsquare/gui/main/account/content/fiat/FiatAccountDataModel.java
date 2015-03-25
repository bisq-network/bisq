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

package io.bitsquare.gui.main.account.content.fiat;

import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.Region;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

class FiatAccountDataModel implements Activatable, DataModel {

    private final User user;
    private final AccountSettings accountSettings;

    final StringProperty title = new SimpleStringProperty();
    final StringProperty holderName = new SimpleStringProperty();
    final StringProperty primaryID = new SimpleStringProperty();
    final StringProperty secondaryID = new SimpleStringProperty();
    final StringProperty primaryIDPrompt = new SimpleStringProperty();
    final StringProperty secondaryIDPrompt = new SimpleStringProperty();
    final StringProperty currencyCode = new SimpleStringProperty();
    final BooleanProperty countryNotInAcceptedCountriesList = new SimpleBooleanProperty();
    final ObjectProperty<FiatAccount.Type> type = new SimpleObjectProperty<>();
    final ObjectProperty<Country> country = new SimpleObjectProperty<>();

    final ObservableList<FiatAccount.Type> allTypes = FXCollections.observableArrayList(FiatAccount.Type
            .getAllBankAccountTypes());
    final ObservableList<FiatAccount> allFiatAccounts = FXCollections.observableArrayList();
    final ObservableList<String> allCurrencyCodes = FXCollections.observableArrayList(CurrencyUtil
            .getAllCurrencyCodes());
    final ObservableList<Region> allRegions = FXCollections.observableArrayList(CountryUtil.getAllRegions());


    @Inject
    public FiatAccountDataModel(User user, AccountSettings accountSettings) {
        this.user = user;
        this.accountSettings = accountSettings;
    }


    @Override
    public void activate() {
        allFiatAccounts.setAll(user.fiatAccountsObservableList());
    }

    @Override
    public void deactivate() {
        // no-op
    }


    void saveBankAccount() {
        FiatAccount fiatAccount = new FiatAccount(type.get(),
                currencyCode.get(),
                country.get(),
                title.get(),
                holderName.get(),
                primaryID.get(),
                secondaryID.get());
        user.addFiatAccount(fiatAccount);
        allFiatAccounts.setAll(user.fiatAccountsObservableList());
        countryNotInAcceptedCountriesList.set(!accountSettings.getAcceptedCountries().contains(country.get()));
        reset();
    }

    void removeBankAccount() {
        user.removeFiatAccount(user.currentFiatAccountPropertyProperty().get());
        allFiatAccounts.setAll(user.fiatAccountsObservableList());
        reset();
    }

    // We ask the user if he likes to add his own bank account country to the accepted country list if he has not
    // already added it before
    void addCountryToAcceptedCountriesList() {
        accountSettings.addAcceptedCountry(country.get());
        countryNotInAcceptedCountriesList.set(false);
    }

    void selectBankAccount(FiatAccount fiatAccount) {
        user.setCurrentFiatAccountProperty(fiatAccount);

        if (fiatAccount != null) {
            title.set(fiatAccount.nameOfBank);
            holderName.set(fiatAccount.accountHolderName);
            primaryID.set(fiatAccount.accountPrimaryID);
            secondaryID.set(fiatAccount.accountSecondaryID);
            primaryIDPrompt.set(fiatAccount.type.primaryId);
            secondaryIDPrompt.set(fiatAccount.type.secondaryId);

            type.set(fiatAccount.type);
            country.set(fiatAccount.country);
            currencyCode.set(fiatAccount.currencyCode);
        }
        else {
            reset();
        }
    }


    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion));
    }


    void setType(FiatAccount.Type type) {
        this.type.set(type);

        if (type != null) {
            primaryIDPrompt.set(type.primaryId);
            secondaryIDPrompt.set(type.secondaryId);
        }
        else {
            primaryIDPrompt.set(null);
            secondaryIDPrompt.set(null);
        }
    }

    void setCountry(Country country) {
        this.country.set(country);
    }

    void setCurrencyCode(String currencyCode) {
        this.currencyCode.set(currencyCode);
    }


    private void reset() {
        title.set(null);
        holderName.set(null);
        primaryID.set(null);
        secondaryID.set(null);
        primaryIDPrompt.set(null);
        secondaryIDPrompt.set(null);

        type.set(null);
        country.set(null);
        currencyCode.set(null);
    }
}
