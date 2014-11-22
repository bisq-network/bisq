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

package io.bitsquare.gui.main.account.content.irc;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.validation.BankAccountNumberValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;

import com.google.inject.Inject;

import java.util.Currency;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

class IrcAccountPM extends PresentationModel<IrcAccountModel> {

    private final InputValidator nickNameValidator;

    final StringProperty ircNickName = new SimpleStringProperty();
    final BooleanProperty saveButtonDisable = new SimpleBooleanProperty(true);
    final ObjectProperty<BankAccountType> type = new SimpleObjectProperty<>();
    final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();


    @Inject
    public IrcAccountPM(IrcAccountModel model, BankAccountNumberValidator nickNameValidator) {
        super(model);
        this.nickNameValidator = nickNameValidator;

        // input
        ircNickName.bindBidirectional(model.nickName);
        type.bindBidirectional(model.type);
        currency.bindBidirectional(model.currency);

        model.nickName.addListener((ov, oldValue, newValue) -> validateInput());
    }


    InputValidator.ValidationResult requestSaveBankAccount() {
        InputValidator.ValidationResult result = validateInput();
        if (result.isValid) {
            model.saveBankAccount();
        }
        return result;
    }

    ObservableList<BankAccount> getAllBankAccounts() {
        return model.allBankAccounts;
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

    String getBankAccountType(BankAccountType bankAccountType) {
        return bankAccountType != null ? BSResources.get(bankAccountType.toString()) : "";
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


    ObservableList<BankAccountType> getAllTypes() {
        return model.allTypes;
    }

    ObservableList<Currency> getAllCurrencies() {
        return model.allCurrencies;
    }

    InputValidator getNickNameValidator() {
        return nickNameValidator;
    }


    void setType(BankAccountType type) {
        model.setType(type);
        validateInput();
    }

    void setCurrency(Currency currency) {
        model.setCurrency(currency);
        validateInput();
    }



    private InputValidator.ValidationResult validateInput() {
        InputValidator.ValidationResult result = nickNameValidator.validate(model.nickName.get());
        if (model.currency.get() == null)
            result = new InputValidator.ValidationResult(false,
                    "You have not selected a currency");
        if (result.isValid) {
            if (model.type.get() == null)
                result = new InputValidator.ValidationResult(false,
                        "You have not selected a payments method");
        }

        saveButtonDisable.set(!result.isValid);
        return result;
    }

}
