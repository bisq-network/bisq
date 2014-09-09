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

package io.bitsquare.gui.model.account.content;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.model.UIModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.Region;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import java.util.Currency;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiatAccountModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(FiatAccountModel.class);

    private final User user;
    private final Settings settings;
    private final Persistence persistence;

    public final StringProperty title = new SimpleStringProperty();
    public final StringProperty holderName = new SimpleStringProperty();
    public final StringProperty primaryID = new SimpleStringProperty();
    public final StringProperty secondaryID = new SimpleStringProperty();
    public final StringProperty primaryIDPrompt = new SimpleStringProperty();
    public final StringProperty secondaryIDPrompt = new SimpleStringProperty();
    public final BooleanProperty countryNotInAcceptedCountriesList = new SimpleBooleanProperty();
    public final ObjectProperty<BankAccountType> type = new SimpleObjectProperty<>();
    public final ObjectProperty<Country> country = new SimpleObjectProperty<>();
    public final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();
    public final ObjectProperty<BankAccount> currentBankAccount = new SimpleObjectProperty<>();

    public final ObservableList<BankAccountType> allTypes = FXCollections.observableArrayList(BankAccountType
            .getAllBankAccountTypes());
    public final ObservableList<BankAccount> allBankAccounts = FXCollections.observableArrayList();
    public final ObservableList<Currency> allCurrencies = FXCollections.observableArrayList(CurrencyUtil
            .getAllCurrencies());
    public final ObservableList<Region> allRegions = FXCollections.observableArrayList(CountryUtil.getAllRegions());


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private FiatAccountModel(User user, Persistence persistence, Settings settings) {
        this.persistence = persistence;
        this.user = user;
        this.settings = settings;
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

        currentBankAccount.set(user.getCurrentBankAccount());
        allBankAccounts.setAll(user.getBankAccounts());
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void saveBankAccount() {
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
        countryNotInAcceptedCountriesList.set(!settings.getAcceptedCountries().contains(country.get()));
        reset();
    }

    public void removeBankAccount() {
        user.removeCurrentBankAccount();
        saveUser();
        allBankAccounts.setAll(user.getBankAccounts());
        reset();
    }

    // We ask the user if he likes to add his own bank account country to the accepted country list if he has not 
    // already added it before
    public void addCountryToAcceptedCountriesList() {
        settings.addAcceptedCountry(country.get());
        saveSettings();
        countryNotInAcceptedCountriesList.set(false);
    }

    public void selectBankAccount(BankAccount bankAccount) {
        currentBankAccount.set(bankAccount);

        user.setCurrentBankAccount(bankAccount);
        persistence.write(user);

        if (bankAccount != null) {
            title.set(bankAccount.getAccountTitle());
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setType(BankAccountType type) {
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

    public void setCountry(Country country) {
        this.country.set(country);
    }

    public void setCurrency(Currency currency) {
        this.currency.set(currency);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        persistence.write(settings);
    }
}
