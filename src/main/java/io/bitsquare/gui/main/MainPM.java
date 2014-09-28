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

package io.bitsquare.gui.main;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MainPM extends PresentationModel<MainModel> {
    private static final Logger log = LoggerFactory.getLogger(MainPM.class);

    final BooleanProperty backendInited = new SimpleBooleanProperty();
    // final StringProperty balance = new SimpleStringProperty();
    final StringProperty bankAccountsComboBoxPrompt = new SimpleStringProperty();
    final BooleanProperty bankAccountsComboBoxDisable = new SimpleBooleanProperty();
    final StringProperty splashScreenInfoText = new SimpleStringProperty();
    final BooleanProperty networkSyncComplete = new SimpleBooleanProperty();
    final IntegerProperty numPendingTrades = new SimpleIntegerProperty();
    private BSFormatter formatter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainPM(MainModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize() {
        super.initialize();

        backendInited.bind(model.backendInited);
        networkSyncComplete.bind(model.networkSyncComplete);
        numPendingTrades.bind(model.numPendingTrades);

        model.networkSyncProgress.addListener((ov, oldValue, newValue) -> {
            if ((double) newValue > 0)
                splashScreenInfoText.set("Synchronise with network " + formatter.formatToPercent((double) newValue));
            else if ((double) newValue == 1)
                splashScreenInfoText.set("Synchronise with network completed.");
            else
                splashScreenInfoText.set("Synchronise with network...");

        });

        /*model.balance.addListener((ov, oldValue, newValue) -> balance.set(formatter.formatCoinWithCode
                (newValue)));*/

        model.getBankAccounts().addListener((ListChangeListener<BankAccount>) change -> {
            bankAccountsComboBoxDisable.set(change.getList().isEmpty());
            bankAccountsComboBoxPrompt.set(change.getList().isEmpty() ? "No accounts" : "");
        });
        bankAccountsComboBoxDisable.set(model.getBankAccounts().isEmpty());
        bankAccountsComboBoxPrompt.set(model.getBankAccounts().isEmpty() ? "No accounts" : "");
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initBackend() {
        model.initBackend();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setCurrentBankAccount(BankAccount bankAccount) {
        model.setCurrentBankAccount(bankAccount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<BankAccount> getBankAccounts() {
        return model.getBankAccounts();
    }

    ObjectProperty<BankAccount> currentBankAccountProperty() {
        return model.currentBankAccountProperty();
    }

    StringConverter<BankAccount> getBankAccountsConverter() {
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

}
