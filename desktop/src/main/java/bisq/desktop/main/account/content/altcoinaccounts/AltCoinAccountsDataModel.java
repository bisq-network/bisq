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

package bisq.desktop.main.account.content.altcoinaccounts;

import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.util.GUIUtil;

import bisq.core.payment.AssetAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.CorruptedDatabaseFilesHandler;

import com.google.inject.Inject;

import javafx.stage.Stage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

class AltCoinAccountsDataModel extends ActivatableDataModel {

    private final PaymentAccountManager paymentAccountManager;
    private final User user;
    private final Preferences preferences;
    final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();
    private final SetChangeListener<PaymentAccount> setChangeListener;
    private final String accountsFileName = "AltcoinPaymentAccounts";
    private final PersistenceProtoResolver persistenceProtoResolver;
    private final CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler;

    @Inject
    public AltCoinAccountsDataModel(PaymentAccountManager paymentAccountManager, User user,
                                    Preferences preferences,
                                    PersistenceProtoResolver persistenceProtoResolver,
                                    CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler) {
        this.paymentAccountManager = paymentAccountManager;
        this.user = user;
        this.preferences = preferences;
        this.persistenceProtoResolver = persistenceProtoResolver;
        this.corruptedDatabaseFilesHandler = corruptedDatabaseFilesHandler;
        setChangeListener = change -> fillAndSortPaymentAccounts();
    }

    @Override
    protected void activate() {
        user.getPaymentAccountsAsObservable().addListener(setChangeListener);
        fillAndSortPaymentAccounts();
    }

    private void fillAndSortPaymentAccounts() {
        if (user.getPaymentAccounts() != null) {
            paymentAccounts.setAll(user.getPaymentAccounts().stream()
                    .filter(paymentAccount -> paymentAccount.getPaymentMethod().isAsset())
                    .collect(Collectors.toList()));
            paymentAccounts.sort(Comparator.comparing(PaymentAccount::getCreationDate));
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
        paymentAccountManager.addPaymentAccount(paymentAccount);
    }

    public boolean onDeleteAccount(PaymentAccount paymentAccount) {
        return paymentAccountManager.removePaymentAccount(paymentAccount);
    }

    public void onSelectAccount(PaymentAccount paymentAccount) {
        user.setCurrentPaymentAccount(paymentAccount);
    }

    public void exportAccounts(Stage stage) {
        if (user.getPaymentAccounts() != null) {
            ArrayList<PaymentAccount> accounts = user.getPaymentAccounts()
                    .stream()
                    .filter(paymentAccount -> paymentAccount instanceof AssetAccount)
                    .collect(Collectors.toCollection(ArrayList::new));
            GUIUtil.exportAccounts(accounts, accountsFileName, preferences, stage, persistenceProtoResolver, corruptedDatabaseFilesHandler);
        }
    }

    public void importAccounts(Stage stage) {
        GUIUtil.importAccounts(user, accountsFileName, preferences, stage, persistenceProtoResolver, corruptedDatabaseFilesHandler);
    }
}
