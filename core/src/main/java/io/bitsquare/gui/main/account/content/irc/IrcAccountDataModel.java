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

package io.bitsquare.gui.main.account.content.irc;

import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.fiat.FiatAccountType;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

class IrcAccountDataModel implements Activatable, DataModel {

    private final User user;

    final StringProperty nickName = new SimpleStringProperty();
    final StringProperty currencyCode = new SimpleStringProperty();
    final ObjectProperty<FiatAccountType> type = new SimpleObjectProperty<>();

    final ObservableList<FiatAccountType> allTypes =
            FXCollections.observableArrayList(FiatAccountType.getAllBankAccountTypes());
    final ObservableList<String> allCurrencyCodes = FXCollections.observableArrayList(CurrencyUtil.getAllCurrencyCodes());
    final ObservableList<FiatAccount> allFiatAccounts = FXCollections.observableArrayList();


    @Inject
    public IrcAccountDataModel(User user) {
        this.user = user;
    }

    @Override
    public void activate() {
        allFiatAccounts.setAll(user.fiatAccountsObservableList());
    }

    @Override
    public void deactivate() {
        // no-op
    }

    void saveBankAccount() {
        FiatAccount fiatAccount = new FiatAccount(type.get(),
                currencyCode.get(),
                CountryUtil.getDefaultCountry(),
                nickName.get(),
                nickName.get(),
                "irc",
                "irc");
        user.addFiatAccount(fiatAccount);
        allFiatAccounts.setAll(user.fiatAccountsObservableList());
        reset();
    }

    void setType(FiatAccountType type) {
        this.type.set(type);
    }

    void setCurrencyCode(String currencyCode) {
        this.currencyCode.set(currencyCode);
    }

    private void reset() {
        nickName.set(null);

        type.set(null);
        currencyCode.set(null);
    }
}
