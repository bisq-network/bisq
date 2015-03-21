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
import io.bitsquare.util.DSAKeyUtil;

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
    private static final long serialVersionUID = 7409078808248518638L;
    private static final Logger log = LoggerFactory.getLogger(User.class);

    private KeyPair p2pSigKeyPair;
    private KeyPair p2pEncryptKeyPair;
    private String accountID;

    // Used for serialisation (ObservableList cannot be serialized) -> serialisation will change anyway so that is
    // only temporary
    private List<FiatAccount> _fiatAccounts = new ArrayList<>();
    private FiatAccount _currentFiatAccount;

    private final transient ObservableList<FiatAccount> fiatAccounts = FXCollections.observableArrayList();
    private final transient ObjectProperty<FiatAccount> currentBankAccount = new SimpleObjectProperty<>();
    transient private EncryptionService encryptionService;

    @Inject
    public User(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        // Used for serialisation (ObservableList cannot be serialized) -> serialisation will change anyway so that is
        // only temporary
        fiatAccounts.addListener((ListChangeListener<FiatAccount>) change -> _fiatAccounts = new ArrayList<>(fiatAccounts));

        currentBankAccount.addListener((ov) -> _currentFiatAccount = currentBankAccount.get());
    }

    // for unit tests
    public User() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyPersistedUser(User persistedUser) {
        if (persistedUser != null) {
            fiatAccounts.setAll(persistedUser.getSerializedBankAccounts());
            setCurrentBankAccount(persistedUser.getSerializedCurrentBankAccount());
            p2pSigKeyPair = persistedUser.getP2pSigKeyPair();
            p2pEncryptKeyPair = persistedUser.getP2pEncryptKeyPair();
            accountID = persistedUser.getAccountId();
        }
        else {
            // First time
            p2pSigKeyPair = DSAKeyUtil.generateDSAKeyPair();
            try {
                p2pEncryptKeyPair = encryptionService.getKeyPair();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
    }

    public void setBankAccount(FiatAccount fiatAccount) {
        // We use the account title as hashCode
        // In case we edit an existing we replace it in the list
        if (fiatAccounts.contains(fiatAccount))
            fiatAccounts.remove(fiatAccount);

        fiatAccounts.add(fiatAccount);
        setCurrentBankAccount(fiatAccount);
    }

    public void removeCurrentBankAccount() {
        if (currentBankAccount.get() != null)
            fiatAccounts.remove(currentBankAccount.get());

        if (fiatAccounts.isEmpty())
            setCurrentBankAccount(null);
        else
            setCurrentBankAccount(fiatAccounts.get(0));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Will be written after registration.
    // Public key from the input for the registration payment tx (or address) will be used
    public void setAccountID(String accountID) {
        this.accountID = accountID;
    }

    public void setCurrentBankAccount(@Nullable FiatAccount fiatAccount) {
        currentBankAccount.set(fiatAccount);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO just a first attempt, refine when working on the embedded data for the reg. tx
    public String getStringifiedBankAccounts() {
        // TODO use steam API
        String bankAccountUIDs = "";
        for (int i = 0; i < fiatAccounts.size(); i++) {
            FiatAccount fiatAccount = fiatAccounts.get(i);
            bankAccountUIDs += fiatAccount.toString();

            if (i < fiatAccounts.size() - 1) {
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

    public ObservableList<FiatAccount> getFiatAccounts() {
        return fiatAccounts;
    }

    public ObjectProperty<FiatAccount> getCurrentBankAccount() {
        return currentBankAccount;
    }

    public FiatAccount getBankAccount(String bankAccountId) {
        for (final FiatAccount fiatAccount : fiatAccounts) {
            if (fiatAccount.getUid().equals(bankAccountId)) {
                return fiatAccount;
            }
        }
        return null;
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

    public ObjectProperty<FiatAccount> currentBankAccountProperty() {
        return currentBankAccount;
    }

    // Used for serialisation (ObservableList cannot be serialized) 
    List<FiatAccount> getSerializedBankAccounts() {
        return _fiatAccounts;
    }

    FiatAccount getSerializedCurrentBankAccount() {
        return _currentFiatAccount;
    }

    public PrivateKey getP2pEncryptPrivateKey() {
        return p2pEncryptKeyPair.getPrivate();
    }

    KeyPair getP2pEncryptKeyPair() {
        return p2pEncryptKeyPair;
    }
}
