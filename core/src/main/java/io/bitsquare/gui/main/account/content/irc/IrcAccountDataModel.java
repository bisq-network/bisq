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

import io.bitsquare.user.AccountSettings;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorMessageService;
import io.bitsquare.arbitration.Reputation;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.fiat.FiatAccountType;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

class IrcAccountDataModel implements Activatable, DataModel {

    private final User user;
    private final AccountSettings accountSettings;
    private final ArbitratorMessageService messageService;
    private final Persistence persistence;

    final StringProperty nickName = new SimpleStringProperty();
    final ObjectProperty<FiatAccountType> type = new SimpleObjectProperty<>();
    final ObjectProperty<Currency> currency = new SimpleObjectProperty<>();

    final ObservableList<FiatAccountType> allTypes =
            FXCollections.observableArrayList(FiatAccountType.getAllBankAccountTypes());
    final ObservableList<Currency> allCurrencies = FXCollections.observableArrayList(CurrencyUtil.getAllCurrencies());
    final ObservableList<FiatAccount> allFiatAccounts = FXCollections.observableArrayList();


    @Inject
    public IrcAccountDataModel(User user, Persistence persistence, AccountSettings accountSettings,
                               ArbitratorMessageService messageService) {
        this.persistence = persistence;
        this.user = user;
        this.accountSettings = accountSettings;
        this.messageService = messageService;

        if (accountSettings.getAcceptedArbitrators().isEmpty())
            addMockArbitrator();
    }

    @Override
    public void activate() {
        allFiatAccounts.setAll(user.getFiatAccounts());
    }

    @Override
    public void deactivate() {
        // no-op
    }

    void saveBankAccount() {
        FiatAccount fiatAccount = new FiatAccount(type.get(),
                currency.get(),
                CountryUtil.getDefaultCountry(),
                nickName.get(),
                nickName.get(),
                "irc",
                "irc");
        user.setBankAccount(fiatAccount);
        saveUser();
        allFiatAccounts.setAll(user.getFiatAccounts());
        reset();
    }

    void setType(FiatAccountType type) {
        this.type.set(type);
    }

    void setCurrency(Currency currency) {
        this.currency.set(currency);
    }

    private void reset() {
        nickName.set(null);

        type.set(null);
        currency.set(null);
    }

    private void saveUser() {
        persistence.write(user);
    }

    private void addMockArbitrator() {
        if (accountSettings.getAcceptedArbitrators().isEmpty() && user.getNetworkKeyPair() != null) {
            byte[] pubKey = new ECKey().getPubKey();
            String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(user.getNetworkPubKey());
            List<Locale> languages = new ArrayList<>();
            languages.add(LanguageUtil.getDefaultLanguageLocale());
            List<Arbitrator.METHOD> arbitrationMethods = new ArrayList<>();
            arbitrationMethods.add(Arbitrator.METHOD.TLS_NOTARY);
            List<Arbitrator.ID_VERIFICATION> idVerifications = new ArrayList<>();
            idVerifications.add(Arbitrator.ID_VERIFICATION.PASSPORT);
            idVerifications.add(Arbitrator.ID_VERIFICATION.GOV_ID);

            Arbitrator arbitrator = new Arbitrator(pubKey,
                    messagePubKeyAsHex,
                    "Manfred Karrer",
                    Arbitrator.ID_TYPE.REAL_LIFE_ID,
                    languages,
                    new Reputation(),
                    Coin.parseCoin("0.001"),
                    arbitrationMethods,
                    idVerifications,
                    "https://bitsquare.io/",
                    "Bla bla...");

            accountSettings.addAcceptedArbitrator(arbitrator);
            persistence.write(accountSettings);

            messageService.addArbitrator(arbitrator);
        }
    }
}
