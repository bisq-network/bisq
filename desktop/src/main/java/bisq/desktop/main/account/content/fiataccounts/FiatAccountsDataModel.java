/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.account.content.fiataccounts;

import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AssetAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import com.google.inject.Inject;

import javafx.stage.Stage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class FiatAccountsDataModel extends ActivatableDataModel {

    private final User user;
    private final Preferences preferences;
    private final OpenOfferManager openOfferManager;
    private final TradeManager tradeManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();
    private final SetChangeListener<PaymentAccount> setChangeListener;
    private final String accountsFileName = "FiatPaymentAccounts";
    private final PersistenceProtoResolver persistenceProtoResolver;
    private final CorruptedStorageFileHandler corruptedStorageFileHandler;

    @Inject
    public FiatAccountsDataModel(User user,
                                 Preferences preferences,
                                 OpenOfferManager openOfferManager,
                                 TradeManager tradeManager,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 PersistenceProtoResolver persistenceProtoResolver,
                                 CorruptedStorageFileHandler corruptedStorageFileHandler) {
        this.user = user;
        this.preferences = preferences;
        this.openOfferManager = openOfferManager;
        this.tradeManager = tradeManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.persistenceProtoResolver = persistenceProtoResolver;
        this.corruptedStorageFileHandler = corruptedStorageFileHandler;
        setChangeListener = change -> fillAndSortPaymentAccounts();
    }

    @Override
    protected void activate() {
        user.getPaymentAccountsAsObservable().addListener(setChangeListener);
        fillAndSortPaymentAccounts();
    }

    private void fillAndSortPaymentAccounts() {
        if (user.getPaymentAccounts() != null) {
            List<PaymentAccount> list = user.getPaymentAccounts().stream()
                    .filter(paymentAccount -> paymentAccount.getPaymentMethod().isFiat())
                    .collect(Collectors.toList());
            paymentAccounts.setAll(list);
            paymentAccounts.sort(Comparator.comparing(PaymentAccount::getAccountName));
        }
    }

    @Override
    protected void deactivate() {
        user.getPaymentAccountsAsObservable().removeListener(setChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSaveNewAccount(PaymentAccount paymentAccount) {
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        List<TradeCurrency> tradeCurrencies = paymentAccount.getTradeCurrencies();
        if (singleTradeCurrency != null) {
            preferences.addFiatCurrency((FiatCurrency) singleTradeCurrency);
        } else if (tradeCurrencies != null && !tradeCurrencies.isEmpty()) {
            if (tradeCurrencies.contains(CurrencyUtil.getDefaultTradeCurrency()))
                paymentAccount.setSelectedTradeCurrency(CurrencyUtil.getDefaultTradeCurrency());
            else
                paymentAccount.setSelectedTradeCurrency(tradeCurrencies.get(0));

            tradeCurrencies.forEach(tradeCurrency -> preferences.addFiatCurrency((FiatCurrency) tradeCurrency));
        }

        user.addPaymentAccount(paymentAccount);
        paymentAccount.onPersistChanges();

        accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());
        accountAgeWitnessService.signAndPublishSameNameAccounts();
    }

    public void onUpdateAccount(PaymentAccount paymentAccount) {
        paymentAccount.onPersistChanges();
        user.requestPersistence();
    }

    public boolean onDeleteAccount(PaymentAccount paymentAccount) {
        boolean usedInOpenOffers = openOfferManager.getObservableList().stream()
                .anyMatch(openOffer -> openOffer.getOffer().getMakerPaymentAccountId().equals(paymentAccount.getId()));

        boolean usedInTrades = tradeManager.getObservableList().stream()
                .anyMatch(trade -> trade.getOffer().getMakerPaymentAccountId().equals(paymentAccount.getId()) ||
                        paymentAccount.getId().equals(trade.getTakerPaymentAccountId()));
        boolean isPaymentAccountUsed = usedInOpenOffers || usedInTrades;

        if (!isPaymentAccountUsed) {
            user.removePaymentAccount(paymentAccount);
        }
        return isPaymentAccountUsed;
    }

    public void onSelectAccount(PaymentAccount paymentAccount) {
        user.setCurrentPaymentAccount(paymentAccount);
    }

    public void exportAccounts(Stage stage) {
        if (user.getPaymentAccounts() != null) {
            ArrayList<PaymentAccount> accounts = user.getPaymentAccounts().stream()
                    .filter(paymentAccount -> !(paymentAccount instanceof AssetAccount))
                    .collect(Collectors.toCollection(ArrayList::new));
            GUIUtil.exportAccounts(accounts, accountsFileName, preferences, stage, persistenceProtoResolver, corruptedStorageFileHandler);
        }
    }

    public void importAccounts(Stage stage) {
        GUIUtil.importAccounts(user, accountsFileName, preferences, stage, persistenceProtoResolver, corruptedStorageFileHandler);
    }

    public int getNumPaymentAccounts() {
        return user.getPaymentAccounts() != null ? user.getPaymentAccounts().size() : 0;
    }
}
