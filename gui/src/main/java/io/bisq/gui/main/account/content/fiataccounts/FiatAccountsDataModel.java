/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.account.content.fiataccounts;

import com.google.inject.Inject;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.payment.CryptoCurrencyAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.gui.common.model.ActivatableDataModel;
import io.bisq.gui.util.GUIUtil;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class FiatAccountsDataModel extends ActivatableDataModel {

    final User user;
    private final Preferences preferences;
    private final OpenOfferManager openOfferManager;
    private final TradeManager tradeManager;
    private final Stage stage;
    final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();
    private final SetChangeListener<PaymentAccount> setChangeListener;
    private final String accountsFileName = "FiatPaymentAccounts";

    @Inject
    public FiatAccountsDataModel(User user, Preferences preferences, OpenOfferManager openOfferManager,
                                 TradeManager tradeManager, Stage stage) {
        this.user = user;
        this.preferences = preferences;
        this.openOfferManager = openOfferManager;
        this.tradeManager = tradeManager;
        this.stage = stage;
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
        GUIUtil.exportAccounts(accounts, accountsFileName, preferences, stage);
    }

    public void importAccounts() {
        GUIUtil.importAccounts(user, accountsFileName, preferences, stage);
    }
}
