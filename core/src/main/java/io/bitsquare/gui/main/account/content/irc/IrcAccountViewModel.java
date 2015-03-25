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

import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;
import io.bitsquare.fiat.FiatAccountType;
import io.bitsquare.gui.util.validation.BankAccountNumberValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

class IrcAccountViewModel extends ActivatableWithDataModel<IrcAccountDataModel> implements ViewModel {

    private final InputValidator nickNameValidator;

    final StringProperty ircNickName = new SimpleStringProperty();
    final StringProperty currencyCode = new SimpleStringProperty();
    final BooleanProperty saveButtonDisable = new SimpleBooleanProperty(true);
    final ObjectProperty<FiatAccountType> type = new SimpleObjectProperty<>();

    @Inject
    public IrcAccountViewModel(IrcAccountDataModel dataModel, BankAccountNumberValidator nickNameValidator) {
        super(dataModel);
        this.nickNameValidator = nickNameValidator;

        // input
        ircNickName.bindBidirectional(dataModel.nickName);
        type.bindBidirectional(dataModel.type);
        currencyCode.bindBidirectional(dataModel.currencyCode);

        dataModel.nickName.addListener((ov, oldValue, newValue) -> validateInput());
    }


    InputValidator.ValidationResult requestSaveBankAccount() {
        InputValidator.ValidationResult result = validateInput();
        if (result.isValid) {
            dataModel.saveBankAccount();
        }
        return result;
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

    String getBankAccountType(FiatAccountType fiatAccountType) {
        return fiatAccountType != null ? BSResources.get(fiatAccountType.toString()) : "";
    }

    StringConverter<String> getCurrencyConverter() {
        return new StringConverter<String>() {
            @Override
            public String toString(String currencyCode) {
                return currencyCode + " (" + CurrencyUtil.getDisplayName(currencyCode) + ")";
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        };
    }


    ObservableList<FiatAccountType> getAllTypes() {
        return dataModel.allTypes;
    }

    ObservableList<String> getAllCurrencyCodes() {
        return dataModel.allCurrencyCodes;
    }

    InputValidator getNickNameValidator() {
        return nickNameValidator;
    }


    void setType(FiatAccountType type) {
        dataModel.setType(type);
        validateInput();
    }

    void setCurrencyCode(String currencyCode) {
        dataModel.setCurrencyCode(currencyCode);
        validateInput();
    }


    private InputValidator.ValidationResult validateInput() {
        InputValidator.ValidationResult result = nickNameValidator.validate(dataModel.nickName.get());
        if (dataModel.currencyCode.get() == null)
            result = new InputValidator.ValidationResult(false,
                    "You have not selected a currency");
        if (result.isValid) {
            if (dataModel.type.get() == null)
                result = new InputValidator.ValidationResult(false,
                        "You have not selected a payments method");
        }

        saveButtonDisable.set(!result.isValid);
        return result;
    }

}
