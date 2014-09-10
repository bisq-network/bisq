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
import io.bitsquare.gui.PresentationModel;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiatAccountPm extends PresentationModel<FiatAccountModel> {
    private static final Logger log = LoggerFactory.getLogger(FiatAccountPm.class);

    private final BankAccountNumberValidator validator = new BankAccountNumberValidator();

    public final StringProperty title = new SimpleStringProperty();
    public final StringProperty holderName = new SimpleStringProperty();
    public final StringProperty primaryID = new SimpleStringProperty();
    public final StringProperty secondaryID = new SimpleStringProperty();
    public final StringProperty primaryIDPrompt = new SimpleStringProperty();
    public final StringProperty secondaryIDPrompt = new SimpleStringProperty();
    public final StringProperty selectionPrompt = new SimpleStringProperty();
    public final BooleanProperty selectionDisable = new SimpleBooleanProperty();
    public final BooleanProperty saveButtonDisable = new SimpleBooleanProperty(true);
    public final ObjectProperty<BankAccountType> type = new SimpleObjectProperty<>();
    public final ObjectProperty<Country> country = new SimpleObjectProperty<>();
    public final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private FiatAccountPm(FiatAccountModel model) {
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

        selectionPrompt.set("No bank account available");
        selectionDisable.set(true);

        model.title.addListener((ov, oldValue, newValue) -> validateInput());
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

    public InputValidator.ValidationResult requestSaveBankAccount() {
        InputValidator.ValidationResult result = validateInput();
        if (result.isValid) {
            model.saveBankAccount();
        }
        return result;
    }

    public void removeBankAccount() {
        model.removeBankAccount();
    }

    public void addCountryToAcceptedCountriesList() {
        model.addCountryToAcceptedCountriesList();
    }

    public void selectBankAccount(BankAccount bankAccount) {
        model.selectBankAccount(bankAccount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Converters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public StringConverter<BankAccountType> getTypesConverter() {
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

    public StringConverter<BankAccount> getSelectionConverter() {
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

    public StringConverter<Currency> getCurrencyConverter() {
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

    public StringConverter<Region> getRegionConverter() {
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

    public StringConverter<Country> getCountryConverter() {
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

    public ObservableList<BankAccountType> getAllTypes() {
        return model.allTypes;
    }

    public ObjectProperty<BankAccount> getCurrentBankAccount() {
        return model.currentBankAccount;
    }

    public ObservableList<BankAccount> getAllBankAccounts() {
        return model.allBankAccounts;
    }

    public ObservableList<Currency> getAllCurrencies() {
        return model.allCurrencies;
    }

    public ObservableList<Region> getAllRegions() {
        return model.allRegions;
    }

    public BooleanProperty getCountryNotInAcceptedCountriesList() {
        return model.countryNotInAcceptedCountriesList;
    }

    public ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return model.getAllCountriesFor(selectedRegion);
    }

    public BankAccountNumberValidator getValidator() {
        return validator;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setType(BankAccountType type) {
        model.setType(type);
        validateInput();
    }

    public void setCountry(Country country) {
        model.setCountry(country);
        validateInput();
    }

    public void setCurrency(Currency currency) {
        model.setCurrency(currency);
        validateInput();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputValidator.ValidationResult validateInput() {
        InputValidator.ValidationResult result = validator.validate(model.title.get());
        if (result.isValid) {
            result = validator.validate(model.holderName.get());
            if (result.isValid) {
                result = validator.validate(model.primaryID.get());
                if (result.isValid) {
                    result = validator.validate(model.secondaryID.get());
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
