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

import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.storage.Storage;

import com.google.common.base.Throwables;

import java.io.Serializable;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The User is persisted locally.
 * It must never be transmitted over the wire (messageKeyPair contains private key!).
 */
public class User implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(User.class);

    transient private Storage<User> storage;
    transient private EncryptionService encryptionService;

    // Persisted fields
    private KeyPair p2pSigKeyPair;
    private KeyPair p2pEncryptKeyPair;
    private String accountID;
    private List<FiatAccount> fiatAccounts = new ArrayList<>();
    private FiatAccount currentFiatAccount;

    // Observable wrappers
    transient private ObservableList<FiatAccount> fiatAccountsObservableList = FXCollections.observableArrayList(fiatAccounts);
    transient private ObjectProperty<FiatAccount> currentFiatAccountProperty = new SimpleObjectProperty<>(currentFiatAccount);

    @Inject
    public User(Storage<User> storage, EncryptionService encryptionService) {
        this.storage = storage;
        this.encryptionService = encryptionService;

        User persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            p2pSigKeyPair = persisted.getP2pSigKeyPair();
            p2pEncryptKeyPair = persisted.getP2pEncryptKeyPair();
            accountID = persisted.getAccountId();

            fiatAccounts = new ArrayList<>(persisted.getFiatAccounts());
            fiatAccountsObservableList.setAll(fiatAccounts);

            currentFiatAccount = persisted.getCurrentFiatAccount();
            currentFiatAccountProperty.set(currentFiatAccount);
        }
        else {
            // First time we create key pairs
            try {
                p2pSigKeyPair = encryptionService.getGeneratedDSAKeyPair();
                p2pEncryptKeyPair = encryptionService.getGeneratedRSAKeyPair();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                log.error(e.getMessage());
                Throwables.propagate(e);
            }
        }
        storage.save();
        // Use that to guarantee update of the serializable field and to make a storage update in case of a change
        fiatAccountsObservableList.addListener((ListChangeListener<FiatAccount>) change -> {
            fiatAccounts = new ArrayList<>(fiatAccountsObservableList);
            storage.save();
        });
        currentFiatAccountProperty.addListener((ov) -> {
            currentFiatAccount = currentFiatAccountProperty.get();
            storage.save();
        });
    }

    // for unit tests
    public User() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param fiatAccount
     * @return If a Fiat Account with the same name already exists we return false. We use the account title as hashCode.
     */
    public boolean addFiatAccount(FiatAccount fiatAccount) {
        if (fiatAccountsObservableList.contains(fiatAccount))
            return false;

        fiatAccountsObservableList.add(fiatAccount);
        setCurrentFiatAccountProperty(fiatAccount);
        return true;
    }

    // In case we edit an existing we remove the existing first
    public void removeFiatAccount(FiatAccount fiatAccount) {
        fiatAccountsObservableList.remove(fiatAccount);

        if (currentFiatAccount.equals(fiatAccount)) {
            if (fiatAccountsObservableList.isEmpty())
                setCurrentFiatAccountProperty(null);
            else
                setCurrentFiatAccountProperty(fiatAccountsObservableList.get(0));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Will be written after registration.
    // Public key from the input for the registration payment tx (or address) will be used
    public void setAccountID(String accountID) {
        this.accountID = accountID;
        storage.save();
    }

    public void setCurrentFiatAccountProperty(@Nullable FiatAccount fiatAccount) {
        currentFiatAccountProperty.set(fiatAccount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO just a first attempt, refine when working on the embedded data for the reg. tx
    public String getStringifiedBankAccounts() {
        String bankAccountUIDs = "";
        for (int i = 0; i < fiatAccountsObservableList.size(); i++) {
            FiatAccount fiatAccount = fiatAccountsObservableList.get(i);
            bankAccountUIDs += fiatAccount.toString();

            if (i < fiatAccountsObservableList.size() - 1) {
                bankAccountUIDs += ", ";
            }
        }
        return bankAccountUIDs;
    }


    public FiatAccount getFiatAccount(String fiatAccountId) {
        for (FiatAccount fiatAccount : fiatAccountsObservableList) {
            if (fiatAccount.id.equals(fiatAccountId)) {
                return fiatAccount;
            }
        }
        return null;
    }

    public String getAccountId() {
        return accountID;
    }

    public boolean isRegistered() {
        return getAccountId() != null;
    }

    public KeyPair getP2pSigKeyPair() {
        return p2pSigKeyPair;
    }

    public PublicKey getP2PSigPubKey() {
        return p2pSigKeyPair.getPublic();
    }

    public PublicKey getP2PEncryptPubKey() {
        return p2pEncryptKeyPair.getPublic();
    }

    public PrivateKey getP2pEncryptPrivateKey() {
        return p2pEncryptKeyPair.getPrivate();
    }

    private KeyPair getP2pEncryptKeyPair() {
        return p2pEncryptKeyPair;
    }

    private List<FiatAccount> getFiatAccounts() {
        return fiatAccounts;
    }

    private FiatAccount getCurrentFiatAccount() {
        return currentFiatAccount;
    }

    public ObjectProperty<FiatAccount> currentFiatAccountProperty() {
        return currentFiatAccountProperty;
    }

    public ObservableList<FiatAccount> fiatAccountsObservableList() {
        return fiatAccountsObservableList;
    }

}
