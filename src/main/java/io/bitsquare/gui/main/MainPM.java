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
import io.bitsquare.network.BootstrapState;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
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

    private final BSFormatter formatter;

    final BooleanProperty backendReady = new SimpleBooleanProperty();
    final StringProperty bankAccountsComboBoxPrompt = new SimpleStringProperty();
    final BooleanProperty bankAccountsComboBoxDisable = new SimpleBooleanProperty();
    final StringProperty blockchainSyncState = new SimpleStringProperty("Initializing");
    final IntegerProperty numPendingTrades = new SimpleIntegerProperty();
    final DoubleProperty blockchainSyncProgress = new SimpleDoubleProperty();
    final BooleanProperty blockchainSyncIndicatorVisible = new SimpleBooleanProperty(true);
    final DoubleProperty bootstrapProgress = new SimpleDoubleProperty(-1);
    final BooleanProperty bootstrapFailed = new SimpleBooleanProperty();
    final BooleanProperty bootstrapIndicatorVisible = new SimpleBooleanProperty(true);
    final StringProperty bootstrapState = new SimpleStringProperty();
    final StringProperty bootstrapErrorMsg = new SimpleStringProperty();
    final StringProperty walletFacadeErrorMsg = new SimpleStringProperty();


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

        backendReady.bind(model.backendReady);
        numPendingTrades.bind(model.numPendingTrades);

        model.bootstrapState.addListener((ov, oldValue, newValue) -> {
                    if (newValue == BootstrapState.DIRECT_SUCCESS ||
                            newValue == BootstrapState.NAT_SUCCESS ||
                            newValue == BootstrapState.RELAY_SUCCESS) {
                        bootstrapState.set("Successfully connected to P2P network: " + newValue.getMessage());
                        bootstrapIndicatorVisible.set(false);
                        bootstrapProgress.set(1);
                    }
                    else if (newValue == BootstrapState.PEER_CREATION_FAILED ||
                            newValue == BootstrapState.DIRECT_FAILED ||
                            newValue == BootstrapState.NAT_FAILED ||
                            newValue == BootstrapState.RELAY_FAILED) {

                        bootstrapErrorMsg.set(newValue.getMessage());
                        bootstrapState.set("Connection to P2P network failed.");
                        bootstrapIndicatorVisible.set(false);
                        bootstrapProgress.set(0);
                        bootstrapFailed.set(true);
                    }
                    else {
                        bootstrapState.set("Connecting to P2P network: " + newValue.getMessage());
                    }
                }
        );

        model.walletFacadeException.addListener((ov, oldValue, newValue) -> {
            blockchainSyncProgress.set(0);
            blockchainSyncIndicatorVisible.set(false);
            blockchainSyncState.set("Startup failed.");
            walletFacadeErrorMsg.set(((Throwable) newValue).getMessage());
        });

        model.networkSyncProgress.addListener((ov, oldValue, newValue) -> setNetworkSyncProgress((double) newValue));
        setNetworkSyncProgress(model.networkSyncProgress.get());

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setNetworkSyncProgress(double value) {
        blockchainSyncProgress.set(value);
        if (value >= 1)
            blockchainSyncState.set("Synchronization completed.");
        else if (value > 0.0)
            blockchainSyncState.set("Synchronizing blockchain: " + formatter.formatToPercent(value));
        else
            blockchainSyncState.set("Connecting to bitcoin network...");

        blockchainSyncIndicatorVisible.set(value < 1);
    }

}
