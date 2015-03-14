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

package io.bitsquare.gui.main.account.content.registration;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.user.User;
import io.bitsquare.viewfx.model.DataModel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import com.google.inject.Inject;

import javax.annotation.Nullable;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RegistrationDataModel implements DataModel {
    private static final Logger log = LoggerFactory.getLogger(RegistrationDataModel.class);

    private final WalletService walletService;
    private final User user;
    private final Persistence persistence;

    private String transactionId;
    private AddressEntry addressEntry;

    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty payFeeSuccess = new SimpleBooleanProperty();
    final StringProperty payFeeErrorMessage = new SimpleStringProperty();


    @Inject
    public RegistrationDataModel(WalletService walletService, User user, Persistence persistence) {

        this.walletService = walletService;
        this.user = user;
        this.persistence = persistence;

        if (walletService != null && walletService.getWallet() != null) {
            addressEntry = walletService.getRegistrationAddressEntry();
            walletService.addBalanceListener(new BalanceListener(getAddressEntry().getAddress()) {
                @Override
                public void onBalanceChanged(@NotNull Coin balance) {
                    updateBalance(balance);
                }
            });
            updateBalance(walletService.getBalanceForAddress(getAddressEntry().getAddress()));
        }
    }

    void payFee() {
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction transaction) {
                log.debug("payRegistrationFee onSuccess");
                if (transaction != null) {
                    transactionId = transaction.getHashAsString();
                    log.info("payRegistrationFee onSuccess tx id:" + transaction.getHashAsString());

                    if (getAddressEntry() != null)
                        user.setAccountID(getAddressEntry().toString());

                    persistence.write(user.getClass().getName(), user);
                    payFeeSuccess.set(true);
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                log.debug("payRegistrationFee onFailure");
                payFeeErrorMessage.set("Fee payment failed with error: " + t.getMessage());
            }
        };
        try {
            walletService.payRegistrationFee(user.getStringifiedBankAccounts(), callback);
        } catch (InsufficientMoneyException e) {
            payFeeErrorMessage.set("Fee payment failed with error: " + e.getMessage());
        }
    }


    WalletService getWalletService() {
        return walletService;
    }

    Coin getFeeAsCoin() {
        return FeePolicy.REGISTRATION_FEE;
    }

    String getTransactionId() {
        return transactionId;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }


    private void updateBalance(@NotNull Coin balance) {
        isWalletFunded.set(balance.compareTo(getFeeAsCoin()) >= 0);
    }

}
