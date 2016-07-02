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

package io.bitsquare.gui.main.account.content.fiataccounts;

import com.google.inject.Inject;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.FiatCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.CryptoCurrencyAccount;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class FiatAccountsDataModel extends ActivatableDataModel {

    final User user;
    private Preferences preferences;
    private final OpenOfferManager openOfferManager;
    private final TradeManager tradeManager;
    private File storageDir;
    final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();
    private final SetChangeListener<PaymentAccount> setChangeListener;

    @Inject
    public FiatAccountsDataModel(User user, Preferences preferences, OpenOfferManager openOfferManager,
                                 TradeManager tradeManager, @Named("storage.dir") File storageDir) {
        this.user = user;
        this.preferences = preferences;
        this.openOfferManager = openOfferManager;
        this.tradeManager = tradeManager;
        this.storageDir = storageDir;
        setChangeListener = change -> fillAndSortPaymentAccounts();
    }

    @Override
    protected void activate() {
        user.getPaymentAccountsAsObservable().addListener(setChangeListener);
        fillAndSortPaymentAccounts();
    }

    private void fillAndSortPaymentAccounts() {
        List<PaymentAccount> list = user.getPaymentAccounts().stream()
                .filter(paymentAccount -> !paymentAccount.getPaymentMethod().getId().equals(PaymentMethod.BLOCK_CHAINS_ID))
                .collect(Collectors.toList());
        paymentAccounts.setAll(list);
        paymentAccounts.sort((o1, o2) -> o1.getCreationDate().compareTo(o2.getCreationDate()));
    }

    @Override
    protected void deactivate() {
        user.getPaymentAccountsAsObservable().removeListener(setChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSaveNewAccount(PaymentAccount paymentAccount) {
        user.addPaymentAccount(paymentAccount);
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        List<TradeCurrency> tradeCurrencies = paymentAccount.getTradeCurrencies();
        if (singleTradeCurrency != null) {
            if (singleTradeCurrency instanceof FiatCurrency)
                preferences.addFiatCurrency((FiatCurrency) singleTradeCurrency);
            else
                preferences.addCryptoCurrency((CryptoCurrency) singleTradeCurrency);
        } else if (tradeCurrencies != null && !tradeCurrencies.isEmpty()) {
            tradeCurrencies.stream().forEach(tradeCurrency -> {
                if (tradeCurrency instanceof FiatCurrency)
                    preferences.addFiatCurrency((FiatCurrency) tradeCurrency);
                else
                    preferences.addCryptoCurrency((CryptoCurrency) tradeCurrency);
            });
        }
    }

    public boolean onDeleteAccount(PaymentAccount paymentAccount) {
        boolean isPaymentAccountUsed = openOfferManager.getOpenOffers().stream()
                .filter(o -> o.getOffer().getOffererPaymentAccountId().equals(paymentAccount.getId()))
                .findAny()
                .isPresent();
        isPaymentAccountUsed = isPaymentAccountUsed || tradeManager.getTrades().stream()
                .filter(t -> t.getOffer().getOffererPaymentAccountId().equals(paymentAccount.getId()) ||
                        paymentAccount.getId().equals(t.getTakerPaymentAccountId()))
                .findAny()
                .isPresent();
        if (!isPaymentAccountUsed)
            user.removePaymentAccount(paymentAccount);
        return isPaymentAccountUsed;
    }

    public void onSelectAccount(PaymentAccount paymentAccount) {
        user.setCurrentPaymentAccount(paymentAccount);
    }

    public void exportAccounts() {
        ArrayList<PaymentAccount> accounts = new ArrayList<>(user.getPaymentAccounts().stream()
                .filter(paymentAccount -> !(paymentAccount instanceof CryptoCurrencyAccount))
                .collect(Collectors.toList()));
        if (!accounts.isEmpty()) {
            Storage<ArrayList<PaymentAccount>> paymentAccountsStorage = new Storage<>(storageDir);
            paymentAccountsStorage.initAndGetPersisted(accounts, "FiatPaymentAccounts");
            paymentAccountsStorage.queueUpForSave(20);
            new Popup<>().feedback("Payment accounts saved to data directory at:\n" + storageDir.getAbsolutePath()).show();
        } else {
            new Popup<>().warning("You have no payment accounts set up for export.").show();
        }
    }

    public void importAccounts() {
        Storage<ArrayList<PaymentAccount>> paymentAccountsStorage = new Storage<>(storageDir);
        ArrayList<PaymentAccount> persisted = paymentAccountsStorage.initAndGetPersisted("FiatPaymentAccounts");
        if (persisted != null) {
            final StringBuilder msg = new StringBuilder();
            persisted.stream().forEach(paymentAccount -> {
                final String id = paymentAccount.getId();

                if (user.getPaymentAccount(id) == null) {
                    user.addPaymentAccount(paymentAccount);
                    msg.append("Payment account with id ").append(id).append("\n");
                } else {
                    msg.append("Payment account with id ").append(id).append(" exists already. We did not import that.").append("\n");
                }
            });
            new Popup<>().feedback("Payment account imported from data directory at:\n" + storageDir.getAbsolutePath() + "\n\nImported accounts:\n" + msg).show();

        } else {
            new Popup<>().warning("No exported payment account has been found at data directory at: " + storageDir.getAbsolutePath()).show();
        }
    }
}
