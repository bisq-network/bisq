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

package io.bitsquare.gui.main.account.content.altcoinaccounts;

import com.google.inject.Inject;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.FiatCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.CryptoCurrencyAccount;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class AltCoinAccountsDataModel extends ActivatableDataModel {

    private final User user;
    private Preferences preferences;
    private final OpenOfferManager openOfferManager;
    private final TradeManager tradeManager;
    private Stage stage;
    final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();
    private final SetChangeListener<PaymentAccount> setChangeListener;
    private final String accountsFileName = "AltcoinPaymentAccounts";

    @Inject
    public AltCoinAccountsDataModel(User user, Preferences preferences, OpenOfferManager openOfferManager,
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
        paymentAccounts.setAll(user.getPaymentAccounts().stream()
                .filter(paymentAccount -> paymentAccount.getPaymentMethod().getId().equals(PaymentMethod.BLOCK_CHAINS_ID))
                .collect(Collectors.toList()));
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
                .filter(paymentAccount -> paymentAccount instanceof CryptoCurrencyAccount)
                .collect(Collectors.toList()));
        GUIUtil.exportAccounts(accounts, accountsFileName, preferences, stage);
    }

    public void importAccounts() {
        GUIUtil.importAccounts(user, accountsFileName, preferences, stage);
    }
}
