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

import io.bitsquare.account.AccountSettings;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.Region;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import java.util.Currency;

import io.bitsquare.viewfx.model.Activatable;
import io.bitsquare.viewfx.model.DataModel;

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
    private final Persistence persistence;

    final StringProperty title = new SimpleStringProperty();
    final StringProperty holderName = new SimpleStringProperty();
    final StringProperty primaryID = new SimpleStringProperty();
    final StringProperty secondaryID = new SimpleStringProperty();
    final StringProperty primaryIDPrompt = new SimpleStringProperty();
    final StringProperty secondaryIDPrompt = new SimpleStringProperty();
    final BooleanProperty countryNotInAcceptedCountriesList = new SimpleBooleanProperty();
    final ObjectProperty<BankAccountType> type = new SimpleObjectProperty<>();
    final ObjectProperty<Country> country = new SimpleObjectProperty<>();
    final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();

    final ObservableList<BankAccountType> allTypes = FXCollections.observableArrayList(BankAccountType
            .getAllBankAccountTypes());
    final ObservableList<BankAccount> allBankAccounts = FXCollections.observableArrayList();
    final ObservableList<Currency> allCurrencies = FXCollections.observableArrayList(CurrencyUtil
            .getAllCurrencies());
    final ObservableList<Region> allRegions = FXCollections.observableArrayList(CountryUtil.getAllRegions());


    @Inject
    public FiatAccountDataModel(User user, Persistence persistence, AccountSettings accountSettings) {
        this.persistence = persistence;
        this.user = user;
        this.accountSettings = accountSettings;
    }


    @Override
    public void activate() {
        allBankAccounts.setAll(user.getBankAccounts());
    }

    @Override
    public void deactivate() {
        // no-op
    }


    void saveBankAccount() {
        BankAccount bankAccount = new BankAccount(type.get(),
                currency.get(),
                country.get(),
                title.get(),
                holderName.get(),
                primaryID.get(),
                secondaryID.get());
        user.setBankAccount(bankAccount);
        saveUser();
        allBankAccounts.setAll(user.getBankAccounts());
        countryNotInAcceptedCountriesList.set(!accountSettings.getAcceptedCountries().contains(country.get()));
        reset();
    }

    void removeBankAccount() {
        user.removeCurrentBankAccount();
        saveUser();
        allBankAccounts.setAll(user.getBankAccounts());
        reset();
    }

    // We ask the user if he likes to add his own bank account country to the accepted country list if he has not
    // already added it before
    void addCountryToAcceptedCountriesList() {
        accountSettings.addAcceptedCountry(country.get());
        saveSettings();
        countryNotInAcceptedCountriesList.set(false);
    }

    void selectBankAccount(BankAccount bankAccount) {
        user.setCurrentBankAccount(bankAccount);
        persistence.write(user);

        if (bankAccount != null) {
            title.set(bankAccount.getNameOfBank());
            holderName.set(bankAccount.getAccountHolderName());
            primaryID.set(bankAccount.getAccountPrimaryID());
            secondaryID.set(bankAccount.getAccountSecondaryID());
            primaryIDPrompt.set(bankAccount.getBankAccountType().getPrimaryId());
            secondaryIDPrompt.set(bankAccount.getBankAccountType().getSecondaryId());

            type.set(bankAccount.getBankAccountType());
            country.set(bankAccount.getCountry());
            currency.set(bankAccount.getCurrency());
        }
        else {
            reset();
        }
    }


    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion));
    }


    void setType(BankAccountType type) {
        this.type.set(type);

        if (type != null) {
            primaryIDPrompt.set(type.getPrimaryId());
            secondaryIDPrompt.set(type.getSecondaryId());
        }
        else {
            primaryIDPrompt.set(null);
            secondaryIDPrompt.set(null);
        }
    }

    void setCountry(Country country) {
        this.country.set(country);
    }

    void setCurrency(Currency currency) {
        this.currency.set(currency);
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
        currency.set(null);
    }

    private void saveUser() {
        persistence.write(user);
    }

    private void saveSettings() {
        persistence.write(accountSettings);
    }
}
