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

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The User is persisted locally it is never transmitted over the wire (messageKeyPair contains private key!).
 */
public class User implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(User.class);
    private static final long serialVersionUID = 7409078808248518638L;

    transient private final IntegerProperty selectedBankAccountIndexProperty = new SimpleIntegerProperty();
    transient private final IntegerProperty bankAccountsSizeProperty = new SimpleIntegerProperty();

    private KeyPair messageKeyPair;
    private String accountID;
    // TODO make it thread safe
    private List<BankAccount> bankAccounts;
    private BankAccount currentBankAccount;

    public User() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyPersistedUser(User persistedUser) {
        if (persistedUser != null) {
            bankAccounts = persistedUser.getBankAccounts();
            messageKeyPair = persistedUser.getMessageKeyPair();
            accountID = persistedUser.getAccountId();
            setCurrentBankAccount(persistedUser.getCurrentBankAccount());
        }
        else {
            // First time
            bankAccounts = new ArrayList<>();
            messageKeyPair = DSAKeyUtil.generateKeyPair();  // DSAKeyUtil.getKeyPair() runs in same thread now
        }

        bankAccountsSizeProperty.set(bankAccounts.size());
    }

    public void addBankAccount(BankAccount bankAccount) {
        if (!bankAccounts.contains(bankAccount)) {
            bankAccounts.add(bankAccount);
            bankAccountsSizeProperty.set(bankAccounts.size());
        }

        setCurrentBankAccount(bankAccount);
    }

    public void removeCurrentBankAccount() {
        if (currentBankAccount != null) {
            bankAccounts.remove(currentBankAccount);
            bankAccountsSizeProperty.set(bankAccounts.size());
        }

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
        currentBankAccount = bankAccount;
        int index = -1;
        for (index = 0; index < bankAccounts.size(); index++) {
            if (currentBankAccount != null && currentBankAccount.equals(bankAccounts.get(index)))
                break;
        }
        selectedBankAccountIndexProperty.set(index);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO just a first attempt, refine when working on the embedded data for the reg. tx
    public String getStringifiedBankAccounts() {
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

    public List<BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    public BankAccount getCurrentBankAccount() {
        return currentBankAccount;
    }

    public BankAccount getBankAccount(String bankAccountId) {
        for (final BankAccount bankAccount : bankAccounts) {
            if (bankAccount.getUid().equals(bankAccountId)) {
                return bankAccount;
            }
        }
        return null;
    }

    public IntegerProperty getSelectedBankAccountIndexProperty() {
        return selectedBankAccountIndexProperty;
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

    public IntegerProperty getBankAccountsSizeProperty() {
        return bankAccountsSizeProperty;
    }
}
