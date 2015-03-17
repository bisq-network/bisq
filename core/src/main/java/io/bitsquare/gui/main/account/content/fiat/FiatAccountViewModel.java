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

import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.fiat.FiatAccountType;
import io.bitsquare.gui.util.validation.BankAccountNumberValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;
import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;

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

class FiatAccountViewModel extends ActivatableWithDataModel<FiatAccountDataModel> implements ViewModel {

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
    final ObjectProperty<FiatAccountType> type = new SimpleObjectProperty<>();
    final ObjectProperty<Country> country = new SimpleObjectProperty<>();
    final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();


    @Inject
    public FiatAccountViewModel(FiatAccountDataModel dataModel, BankAccountNumberValidator bankAccountNumberValidator) {
        super(dataModel);
        this.bankAccountNumberValidator = bankAccountNumberValidator;

        // input
        title.bindBidirectional(dataModel.title);
        holderName.bindBidirectional(dataModel.holderName);
        primaryID.bindBidirectional(dataModel.primaryID);
        secondaryID.bindBidirectional(dataModel.secondaryID);
        type.bindBidirectional(dataModel.type);
        country.bindBidirectional(dataModel.country);
        currency.bindBidirectional(dataModel.currency);

        primaryIDPrompt.bind(dataModel.primaryIDPrompt);
        secondaryIDPrompt.bind(dataModel.secondaryIDPrompt);

        selectionPrompt.set("No bank account available");
        selectionDisable.set(true);

        dataModel.title.addListener((ov, oldValue, newValue) -> validateInput());
        holderName.addListener((ov, oldValue, newValue) -> validateInput());
        primaryID.addListener((ov, oldValue, newValue) -> validateInput());
        secondaryID.addListener((ov, oldValue, newValue) -> validateInput());
    }

    @Override
    public void doActivate() {
        dataModel.allFiatAccounts.addListener((ListChangeListener<FiatAccount>) change -> applyAllBankAccounts());
        applyAllBankAccounts();
    }


    InputValidator.ValidationResult requestSaveBankAccount() {
        InputValidator.ValidationResult result = validateInput();
        if (result.isValid) {
            dataModel.saveBankAccount();
        }
        return result;
    }

    void removeBankAccount() {
        dataModel.removeBankAccount();
    }

    void addCountryToAcceptedCountriesList() {
        dataModel.addCountryToAcceptedCountriesList();
    }

    void selectBankAccount(FiatAccount fiatAccount) {
        dataModel.selectBankAccount(fiatAccount);
    }


    StringConverter<FiatAccountType> getTypesConverter() {
        return new StringConverter<FiatAccountType>() {
            @Override
            public String toString(FiatAccountType TypeInfo) {
                return BSResources.get(TypeInfo.toString());
            }

            @Override
            public FiatAccountType fromString(String s) {
                return null;
            }
        };
    }

    StringConverter<FiatAccount> getSelectionConverter() {
        return new StringConverter<FiatAccount>() {
            @Override
            public String toString(FiatAccount fiatAccount) {
                return fiatAccount.getNameOfBank();
            }

            @Override
            public FiatAccount fromString(String s) {
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


    ObservableList<FiatAccountType> getAllTypes() {
        return dataModel.allTypes;
    }

    ObservableList<FiatAccount> getAllBankAccounts() {
        return dataModel.allFiatAccounts;
    }

    ObservableList<Currency> getAllCurrencies() {
        return dataModel.allCurrencies;
    }

    ObservableList<Region> getAllRegions() {
        return dataModel.allRegions;
    }

    BooleanProperty getCountryNotInAcceptedCountriesList() {
        return dataModel.countryNotInAcceptedCountriesList;
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return dataModel.getAllCountriesFor(selectedRegion);
    }

    BankAccountNumberValidator getBankAccountNumberValidator() {
        return bankAccountNumberValidator;
    }


    void setType(FiatAccountType type) {
        dataModel.setType(type);
        validateInput();
    }

    void setCountry(Country country) {
        dataModel.setCountry(country);
        validateInput();
    }

    void setCurrency(Currency currency) {
        dataModel.setCurrency(currency);
        validateInput();
    }


    private void applyAllBankAccounts() {
        if (dataModel.allFiatAccounts.isEmpty()) {
            selectionPrompt.set("No bank account available");
            selectionDisable.set(true);
        }
        else {
            selectionPrompt.set("Select bank account");
            selectionDisable.set(false);
        }
    }

    private InputValidator.ValidationResult validateInput() {
        InputValidator.ValidationResult result = bankAccountNumberValidator.validate(dataModel.title.get());
        if (result.isValid) {
            result = bankAccountNumberValidator.validate(dataModel.holderName.get());
            if (result.isValid) {
                result = bankAccountNumberValidator.validate(dataModel.primaryID.get());
                if (result.isValid) {
                    result = bankAccountNumberValidator.validate(dataModel.secondaryID.get());
                    if (result.isValid) {
                        if (dataModel.currency.get() == null)
                            result = new InputValidator.ValidationResult(false,
                                    "You have not selected a currency");
                        if (result.isValid) {
                            if (dataModel.country.get() == null)
                                result = new InputValidator.ValidationResult(false,
                                        "You have not selected a country of the payments account");
                            if (result.isValid) {
                                if (dataModel.type.get() == null)
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
