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

package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.util.DSAKeyUtil;

import java.io.Serializable;

import java.security.KeyPair;
import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * The User is persisted locally.
 * It must never be transmitted over the wire (messageKeyPair contains private key!).
 */
public class User implements Serializable {
    private static final long serialVersionUID = 7409078808248518638L;

    private KeyPair messageKeyPair;
    private String accountID;

    // Used for serialisation (ObservableList cannot be serialized) -> serialisation will change anyway so that is
    // only temporary
    private List<BankAccount> _bankAccounts = new ArrayList<>();
    private BankAccount _currentBankAccount;

    private final transient ObservableList<BankAccount> bankAccounts = FXCollections.observableArrayList();
    private final transient ObjectProperty<BankAccount> currentBankAccount = new SimpleObjectProperty<>();

    public User() {
        // Used for serialisation (ObservableList cannot be serialized) -> serialisation will change anyway so that is
        // only temporary
        bankAccounts.addListener((ListChangeListener<BankAccount>) change ->
                _bankAccounts = new ArrayList<>(bankAccounts));

        currentBankAccount.addListener((ov) -> _currentBankAccount = currentBankAccount.get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyPersistedUser(User persistedUser) {
        if (persistedUser != null) {
            bankAccounts.setAll(persistedUser.getSerializedBankAccounts());
            setCurrentBankAccount(persistedUser.getSerializedCurrentBankAccount());
            messageKeyPair = persistedUser.getMessageKeyPair();
            accountID = persistedUser.getAccountId();
        }
        else {
            // First time
            // TODO use separate thread. DSAKeyUtil.getKeyPair() runs in same thread now
            messageKeyPair = DSAKeyUtil.generateKeyPair();
        }
    }

    public void setBankAccount(BankAccount bankAccount) {
        // We use the account title as hashCode
        // In case we edit an existing we replace it in the list
        if (bankAccounts.contains(bankAccount))
            bankAccounts.remove(bankAccount);

        bankAccounts.add(bankAccount);
        setCurrentBankAccount(bankAccount);
    }

    public void removeCurrentBankAccount() {
        if (currentBankAccount.get() != null)
            bankAccounts.remove(currentBankAccount.get());

        if (bankAccounts.isEmpty())
            setCurrentBankAccount(null);
        else
            setCurrentBankAccount(bankAccounts.get(0));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Will be written after registration.
    // Public key from the input for the registration payment tx (or address) will be used
    public void setAccountID(String accountID) {
        this.accountID = accountID;
    }

    public void setCurrentBankAccount(@Nullable BankAccount bankAccount) {
        currentBankAccount.set(bankAccount);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO just a first attempt, refine when working on the embedded data for the reg. tx
    public String getStringifiedBankAccounts() {
        // TODO use steam API
        String bankAccountUIDs = "";
        for (int i = 0; i < bankAccounts.size(); i++) {
            BankAccount bankAccount = bankAccounts.get(i);
            bankAccountUIDs += bankAccount.toString();

            if (i < bankAccounts.size() - 1) {
                bankAccountUIDs += ", ";
            }
        }
        return bankAccountUIDs;
    }

    public String getAccountId() {
        return accountID;
    }

    public boolean isRegistered() {
        return getAccountId() != null;
    }

    public ObservableList<BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    public ObjectProperty<BankAccount> getCurrentBankAccount() {
        return currentBankAccount;
    }

    public BankAccount getBankAccount(String bankAccountId) {
        // TODO use steam API
        for (final BankAccount bankAccount : bankAccounts) {
            if (bankAccount.getUid().equals(bankAccountId)) {
                return bankAccount;
            }
        }
        return null;
    }

    public KeyPair getMessageKeyPair() {
        return messageKeyPair;
    }

    public PublicKey getMessagePublicKey() {
        return messageKeyPair.getPublic();
    }

    public String getMessagePublicKeyAsString() {
        return DSAKeyUtil.getHexStringFromPublicKey(getMessagePublicKey());
    }

    public ObjectProperty<BankAccount> currentBankAccountProperty() {
        return currentBankAccount;
    }

    // Used for serialisation (ObservableList cannot be serialized) -> serialisation will change anyway so that is
    // only temporary
    List<BankAccount> getSerializedBankAccounts() {
        return _bankAccounts;
    }

    BankAccount getSerializedCurrentBankAccount() {
        return _currentBankAccount;
    }

}
