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

import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.ActivatableWithDelegate;
import io.bitsquare.gui.ViewModel;
import io.bitsquare.gui.util.validation.BankAccountNumberValidator;
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

class FiatAccountPM extends ActivatableWithDelegate<FiatAccountModel> implements ViewModel {

    private final BankAccountNumberValidator bankAccountNumberValidator;

    final StringProperty title = new SimpleStringProperty();
    final StringProperty holderName = new SimpleStringProperty();
    final StringProperty primaryID = new SimpleStringProperty();
    final StringProperty secondaryID = new SimpleStringProperty();
    final StringProperty primaryIDPrompt = new SimpleStringProperty();
    final StringProperty secondaryIDPrompt = new SimpleStringProperty();
    final StringProperty selectionPrompt = new SimpleStringProperty();
    final BooleanProperty selectionDisable = new SimpleBooleanProperty();
    final BooleanProperty saveButtonDisable = new SimpleBooleanProperty(true);
    final ObjectProperty<BankAccountType> type = new SimpleObjectProperty<>();
    final ObjectProperty<Country> country = new SimpleObjectProperty<>();
    final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();


    @Inject
    public FiatAccountPM(FiatAccountModel model, BankAccountNumberValidator bankAccountNumberValidator) {
        super(model);
        this.bankAccountNumberValidator = bankAccountNumberValidator;

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

        selectionPrompt.set("No bank account available");
        selectionDisable.set(true);

        model.title.addListener((ov, oldValue, newValue) -> validateInput());
        holderName.addListener((ov, oldValue, newValue) -> validateInput());
        primaryID.addListener((ov, oldValue, newValue) -> validateInput());
        secondaryID.addListener((ov, oldValue, newValue) -> validateInput());
    }

    @Override
    public void doActivate() {
        delegate.allBankAccounts.addListener((ListChangeListener<BankAccount>) change -> applyAllBankAccounts());
        applyAllBankAccounts();
    }


    InputValidator.ValidationResult requestSaveBankAccount() {
        InputValidator.ValidationResult result = validateInput();
        if (result.isValid) {
            delegate.saveBankAccount();
        }
        return result;
    }

    void removeBankAccount() {
        delegate.removeBankAccount();
    }

    void addCountryToAcceptedCountriesList() {
        delegate.addCountryToAcceptedCountriesList();
    }

    void selectBankAccount(BankAccount bankAccount) {
        delegate.selectBankAccount(bankAccount);
    }


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
                return bankAccount.getNameOfBank();
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


    ObservableList<BankAccountType> getAllTypes() {
        return delegate.allTypes;
    }

    ObservableList<BankAccount> getAllBankAccounts() {
        return delegate.allBankAccounts;
    }

    ObservableList<Currency> getAllCurrencies() {
        return delegate.allCurrencies;
    }

    ObservableList<Region> getAllRegions() {
        return delegate.allRegions;
    }

    BooleanProperty getCountryNotInAcceptedCountriesList() {
        return delegate.countryNotInAcceptedCountriesList;
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return delegate.getAllCountriesFor(selectedRegion);
    }

    BankAccountNumberValidator getBankAccountNumberValidator() {
        return bankAccountNumberValidator;
    }


    void setType(BankAccountType type) {
        delegate.setType(type);
        validateInput();
    }

    void setCountry(Country country) {
        delegate.setCountry(country);
        validateInput();
    }

    void setCurrency(Currency currency) {
        delegate.setCurrency(currency);
        validateInput();
    }


    private void applyAllBankAccounts() {
        if (delegate.allBankAccounts.isEmpty()) {
            selectionPrompt.set("No bank account available");
            selectionDisable.set(true);
        }
        else {
            selectionPrompt.set("Select bank account");
            selectionDisable.set(false);
        }
    }

    private InputValidator.ValidationResult validateInput() {
        InputValidator.ValidationResult result = bankAccountNumberValidator.validate(delegate.title.get());
        if (result.isValid) {
            result = bankAccountNumberValidator.validate(delegate.holderName.get());
            if (result.isValid) {
                result = bankAccountNumberValidator.validate(delegate.primaryID.get());
                if (result.isValid) {
                    result = bankAccountNumberValidator.validate(delegate.secondaryID.get());
                    if (result.isValid) {
                        if (delegate.currency.get() == null)
                            result = new InputValidator.ValidationResult(false,
                                    "You have not selected a currency");
                        if (result.isValid) {
                            if (delegate.country.get() == null)
                                result = new InputValidator.ValidationResult(false,
                                        "You have not selected a country of the payments account");
                            if (result.isValid) {
                                if (delegate.type.get() == null)
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
