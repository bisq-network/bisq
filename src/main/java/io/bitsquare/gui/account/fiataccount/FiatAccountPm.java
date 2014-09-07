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

package io.bitsquare.gui.account.fiataccount;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.validation.BankAccountValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import com.google.inject.Inject;

import java.util.Currency;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiatAccountPm extends PresentationModel<FiatAccountModel> {
    private static final Logger log = LoggerFactory.getLogger(FiatAccountPm.class);

    private BankAccountValidator bankAccountValidator = new BankAccountValidator();

    StringProperty title = new SimpleStringProperty();
    StringProperty holderName = new SimpleStringProperty();
    StringProperty primaryID = new SimpleStringProperty();
    StringProperty secondaryID = new SimpleStringProperty();
    StringProperty primaryIDPrompt = new SimpleStringProperty();
    StringProperty secondaryIDPrompt = new SimpleStringProperty();
    StringProperty selectionPrompt = new SimpleStringProperty();
    BooleanProperty selectionDisable = new SimpleBooleanProperty();
    BooleanProperty saveButtonDisable = new SimpleBooleanProperty(true);
    BooleanProperty addBankAccountButtonDisable = new SimpleBooleanProperty(true);
    BooleanProperty changeBankAccountButtonDisable = new SimpleBooleanProperty(true);
    BooleanProperty removeBankAccountButtonDisable = new SimpleBooleanProperty(true);

    BooleanProperty countryNotInAcceptedCountriesList = new SimpleBooleanProperty();
    ObjectProperty<BankAccountType> type = new SimpleObjectProperty<>();
    ObjectProperty<Country> country = new SimpleObjectProperty<>();
    ObjectProperty<Currency> currency = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FiatAccountPm(FiatAccountModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialized() {
        super.initialized();

        // input
        title.bindBidirectional(model.title);
        holderName.bindBidirectional(model.holderName);
        primaryID.bindBidirectional(model.primaryID);
        secondaryID.bindBidirectional(model.secondaryID);
        type.bindBidirectional(model.type);
        country.bindBidirectional(model.country);
        currency.bindBidirectional(model.currency);

        primaryIDPrompt.bind(model.primaryIDPrompt);
        secondaryIDPrompt.bind(model.secondaryIDPrompt);
        countryNotInAcceptedCountriesList.bind(model.countryNotInAcceptedCountriesList);

        selectionPrompt.set("No bank account available");
        selectionDisable.set(true);

        model.title.addListener((ov, oldValue, newValue) -> {
            validateInput();
            /*
            InputValidator.ValidationResult result = validateInput();
            if (result.isValid) {
                result = bankAccountValidator.validate(newValue);
                saveButtonDisable.set(!result.isValid);
            }*/
        });
        holderName.addListener((ov, oldValue, newValue) -> validateInput());
        primaryID.addListener((ov, oldValue, newValue) -> validateInput());
        secondaryID.addListener((ov, oldValue, newValue) -> validateInput());
    }

    @Override
    public void activate() {
        super.activate();


        model.allBankAccounts.addListener((ListChangeListener<BankAccount>) change -> {
            if (model.allBankAccounts.isEmpty()) {
                selectionPrompt.set("No bank account available");
                selectionDisable.set(true);
            }
            else {
                selectionPrompt.set("Select bank account");
                selectionDisable.set(false);
            }
        });
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    InputValidator.ValidationResult saveBankAccount() {
        InputValidator.ValidationResult result = validateInput();
        if (result.isValid) {
            model.saveBankAccount();

            addBankAccountButtonDisable.set(false);
            changeBankAccountButtonDisable.set(false);
            removeBankAccountButtonDisable.set(false);
        }
        return result;
    }

    void removeBankAccount() {
        model.removeBankAccount();
    }

    void updateDoneButtonDisabled() {
      /*  boolean isValid = model.languageList != null && model.languageList.size() > 0 &&
                model.countryList != null && model.countryList.size() > 0 &&
                model.arbitratorList != null && model.arbitratorList.size() > -1;
        doneButtonDisabled.set(!isValid);*/
    }


    void addCountryToAcceptedCountriesList() {
        model.addCountryToAcceptedCountriesList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Converters
    ///////////////////////////////////////////////////////////////////////////////////////////

    StringConverter<BankAccountType> getTypesConverter() {
        return new StringConverter<BankAccountType>() {
            @Override
            public String toString(BankAccountType TypeInfo) {
                return BSResources.get(TypeInfo.toString());
            }

            @Override
            public BankAccountType fromString(String s) {
                return null;
            }
        };
    }

    StringConverter<BankAccount> getSelectionConverter() {
        return new StringConverter<BankAccount>() {
            @Override
            public String toString(BankAccount bankAccount) {
                return bankAccount.getAccountTitle();
            }

            @Override
            public BankAccount fromString(String s) {
                return null;
            }
        };
    }

    StringConverter<Currency> getCurrencyConverter() {
        return new StringConverter<Currency>() {

            @Override
            public String toString(Currency currency) {
                return currency.getCurrencyCode() + " (" + currency.getDisplayName() + ")";
            }


            @Override
            public Currency fromString(String s) {
                return null;
            }
        };
    }

    StringConverter<Region> getRegionConverter() {
        return new StringConverter<io.bitsquare.locale.Region>() {
            @Override
            public String toString(io.bitsquare.locale.Region region) {
                return region.getName();
            }

            @Override
            public io.bitsquare.locale.Region fromString(String s) {
                return null;
            }
        };
    }

    StringConverter<Country> getCountryConverter() {
        return new StringConverter<Country>() {
            @Override
            public String toString(Country country) {
                return country.getName();
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<BankAccountType> getAllTypes() {
        return model.allTypes;
    }

    ObjectProperty<BankAccount> getCurrentBankAccount() {
        return model.currentBankAccount;
    }

    ObservableList<BankAccount> getAllBankAccounts() {
        return model.allBankAccounts;
    }

    ObservableList<Currency> getAllCurrencies() {
        return model.allCurrencies;
    }

    ObservableList<Region> getAllRegions() {
        return model.allRegions;
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return model.getAllCountriesFor(selectedRegion);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setCurrentBankAccount(BankAccount bankAccount) {
        model.setCurrentBankAccount(bankAccount);
        validateInput();
    }

    void setType(BankAccountType type) {
        model.setType(type);
        validateInput();
    }

    void setCountry(Country country) {
        model.setCountry(country);
        validateInput();
    }

    void setCurrency(Currency currency) {
        model.setCurrency(currency);
        validateInput();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    private InputValidator.ValidationResult validateInput() {
        InputValidator.ValidationResult result = bankAccountValidator.validate(model.title.get());
        if (result.isValid) {
            result = bankAccountValidator.validate(model.holderName.get());
            if (result.isValid) {
                result = bankAccountValidator.validate(model.primaryID.get());
                if (result.isValid) {
                    result = bankAccountValidator.validate(model.secondaryID.get());
                    if (result.isValid) {
                        if (model.currency.get() == null)
                            result = new InputValidator.ValidationResult(false,
                                    "You have not selected a currency");
                        if (result.isValid) {
                            if (model.country.get() == null)
                                result = new InputValidator.ValidationResult(false,
                                        "You have not selected a country of the payments account");
                            if (result.isValid) {
                                if (model.type.get() == null)
                                    result = new InputValidator.ValidationResult(false,
                                            "You have not selected a payments method");
                            }
                        }
                    }
                }
            }
        }

        saveButtonDisable.set(!result.isValid);
        return result;
    }

}
