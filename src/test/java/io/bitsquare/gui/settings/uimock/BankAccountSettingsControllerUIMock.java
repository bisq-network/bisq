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

package io.bitsquare.gui.settings.uimock;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.Region;

import java.net.URL;

import java.util.Currency;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.StringConverter;

public class BankAccountSettingsControllerUIMock implements Initializable {

    @FXML private TextField bankAccountTitleTextField, bankAccountHolderNameTextField, bankAccountPrimaryIDTextField,
            bankAccountSecondaryIDTextField;
    @FXML private Button saveBankAccountButton, addBankAccountButton;
    @FXML private ComboBox<Country> bankAccountCountryComboBox;
    @FXML private ComboBox<Region> bankAccountRegionComboBox;
    @FXML private ComboBox<BankAccount> bankAccountComboBox;
    @FXML private ComboBox<BankAccountType> bankAccountTypesComboBox;
    @FXML private ComboBox<Currency> bankAccountCurrencyComboBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initBankAccountComboBox();
        initBankAccountTypesComboBox();
        initBankAccountCurrencyComboBox();
        initBankAccountCountryComboBox();
    }

    private void initBankAccountComboBox() {
        bankAccountComboBox.setPromptText("No bank account available");
        bankAccountComboBox.setDisable(true);
    }

    private void initBankAccountTypesComboBox() {
        bankAccountTypesComboBox.setItems(FXCollections.observableArrayList(BankAccountType.getAllBankAccountTypes()));
        bankAccountTypesComboBox.setConverter(new StringConverter<BankAccountType>() {
            @Override
            public String toString(BankAccountType bankAccountTypeInfo) {
                return BSResources.get(bankAccountTypeInfo.toString());
            }

            @Override
            public BankAccountType fromString(String s) {
                return null;
            }
        });
    }

    private void initBankAccountCurrencyComboBox() {
        bankAccountCurrencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllCurrencies()));
        bankAccountCurrencyComboBox.setConverter(new StringConverter<Currency>() {
            @Override
            public String toString(Currency currency) {
                return currency.getCurrencyCode() + " (" + currency.getDisplayName() + ")";
            }

            @Override
            public Currency fromString(String s) {
                return null;
            }
        });
    }

    private void initBankAccountCountryComboBox() {
        bankAccountRegionComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllRegions()));
        bankAccountRegionComboBox.setConverter(new StringConverter<Region>() {
            @Override
            public String toString(Region region) {
                return region.getName();
            }

            @Override
            public Region fromString(String s) {
                return null;
            }
        });

        bankAccountCountryComboBox.setConverter(new StringConverter<Country>() {
            @Override
            public String toString(Country country) {
                return country.getName();
            }


            @Override
            public Country fromString(String s) {
                return null;
            }
        });
    }


}

