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
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainPM extends PresentationModel<MainModel> {
    private static final Logger log = LoggerFactory.getLogger(MainPM.class);

    public final BooleanProperty backendInited = new SimpleBooleanProperty();
    public final StringProperty balance = new SimpleStringProperty();
    public final StringProperty bankAccountsComboBoxPrompt = new SimpleStringProperty();
    public final BooleanProperty bankAccountsComboBoxDisable = new SimpleBooleanProperty();
    public final StringProperty splashScreenInfoText = new SimpleStringProperty();
    public final BooleanProperty networkSyncComplete = new SimpleBooleanProperty();
    public final BooleanProperty takeOfferRequested = new SimpleBooleanProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainPM(MainModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialized() {
        super.initialized();

        backendInited.bind(model.backendInited);
        networkSyncComplete.bind(model.networkSyncComplete);
        takeOfferRequested.bind(model.takeOfferRequested);

        model.networkSyncProgress.addListener((ov, oldValue, newValue) -> {
            if ((double) newValue > 0)
                splashScreenInfoText.set("Synchronise with network " + BSFormatter.formatToPercent((double) newValue));
            else if ((double) newValue == 1)
                splashScreenInfoText.set("Synchronise with network completed.");
            else
                splashScreenInfoText.set("Synchronise with network...");

        });

        model.balance.addListener((ov, oldValue, newValue) -> balance.set(BSFormatter.formatCoinWithCode
                (newValue)));

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

    public void initBackend() {
        model.initBackend();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSelectedNavigationItem(NavigationItem navigationItem) {
        model.setSelectedNavigationItem(navigationItem);
    }

    public void setCurrentBankAccount(BankAccount bankAccount) {
        model.setCurrentBankAccount(bankAccount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public NavigationItem[] getSelectedNavigationItems() {
        return model.getSelectedNavigationItems();
    }

    public ObservableList<BankAccount> getBankAccounts() {
        return model.getBankAccounts();
    }

    public ObjectProperty<BankAccount> currentBankAccountProperty() {
        return model.currentBankAccountProperty();
    }

    public StringConverter<BankAccount> getBankAccountsConverter() {
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

}
