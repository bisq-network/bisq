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

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;

import bisq.core.payment.PaymentAccount;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

class AltCoinAccountsViewModel extends ActivatableWithDataModel<AltCoinAccountsDataModel> implements ViewModel {

    @Inject
    public AltCoinAccountsViewModel(AltCoinAccountsDataModel dataModel) {
        super(dataModel);
    }

    @Override
    protected void activate() {
    }

    @Override
    protected void deactivate() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSaveNewAccount(PaymentAccount paymentAccount) {
        dataModel.onSaveNewAccount(paymentAccount);
    }

    public boolean onDeleteAccount(PaymentAccount paymentAccount) {
        return dataModel.onDeleteAccount(paymentAccount);
    }

    public void onSelectAccount(PaymentAccount paymentAccount) {
        dataModel.onSelectAccount(paymentAccount);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<PaymentAccount> getPaymentAccounts() {
        return dataModel.paymentAccounts;
    }
}
