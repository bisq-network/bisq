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

package io.bitsquare.gui.main.offer.createoffer;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.model.DataModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.Country;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import java.util.UUID;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel implements Activatable, DataModel {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferDataModel.class);


    private final OpenOfferManager openOfferManager;
    private final WalletService walletService;
    private final AccountSettings accountSettings;
    private final Preferences preferences;
    private final User user;
    private final BSFormatter formatter;
    private final String offerId;
    private final AddressEntry addressEntry;
    final ObjectProperty<Coin> offerFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> networkFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> securityDepositAsCoin = new SimpleObjectProperty<>();
    private final BalanceListener balanceListener;
    private final ChangeListener<FiatAccount> currentFiatAccountListener;

    private Offer.Direction direction;

    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty requestPlaceOfferSuccess = new SimpleBooleanProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty useMBTC = new SimpleBooleanProperty();

    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> minAmountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();

    final ObservableList<Country> acceptedCountries = FXCollections.observableArrayList();
    final ObservableList<String> acceptedLanguageCodes = FXCollections.observableArrayList();
    final ObservableList<Arbitrator> acceptedArbitrators = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    CreateOfferDataModel(OpenOfferManager openOfferManager, WalletService walletService,
                         AccountSettings accountSettings, Preferences preferences, User user, BSFormatter formatter) {
        this.openOfferManager = openOfferManager;
        this.walletService = walletService;
        this.accountSettings = accountSettings;
        this.preferences = preferences;
        this.user = user;
        this.formatter = formatter;
        this.offerId = UUID.randomUUID().toString();

        addressEntry = walletService.getAddressEntry(offerId);

        offerFeeAsCoin.set(FeePolicy.CREATE_OFFER_FEE);
        networkFeeAsCoin.set(FeePolicy.TX_FEE);

        // we need to set it here already as it is used before activate
        securityDepositAsCoin.set(accountSettings.getSecurityDeposit());

        balanceListener = new BalanceListener(getAddressEntry().getAddress()) {
            @Override
            public void onBalanceChanged(@NotNull Coin balance) {
                updateBalance(balance);
            }
        };

        currentFiatAccountListener = (observable, oldValue, newValue) -> {
            applyBankAccount(newValue);
        };
    }

    @Override
    public void activate() {
        addBindings();
        addListeners();

        // might be changed after screen change
        if (accountSettings != null) {
            // set it here again to cover the case of an securityDeposit change after a screen change
            securityDepositAsCoin.set(accountSettings.getSecurityDeposit());

            acceptedCountries.setAll(accountSettings.getAcceptedCountries());
            acceptedLanguageCodes.setAll(accountSettings.getAcceptedLanguageLocaleCodes());
            acceptedArbitrators.setAll(accountSettings.getAcceptedArbitrators());
        }

        updateBalance(walletService.getBalanceForAddress(getAddressEntry().getAddress()));
        applyBankAccount(user.currentFiatAccountProperty().get());
    }

    @Override
    public void deactivate() {
        removeBindings();
        removeListeners();
    }

    private void addBindings() {
        btcCode.bind(preferences.btcDenominationProperty());
    }

    private void removeBindings() {
        btcCode.unbind();
    }

    private void addListeners() {
        walletService.addBalanceListener(balanceListener);
        user.currentFiatAccountProperty().addListener(currentFiatAccountListener);

    }

    private void removeListeners() {
        walletService.removeBalanceListener(balanceListener);
        user.currentFiatAccountProperty().removeListener(currentFiatAccountListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithData(Offer.Direction direction) {
        this.direction = direction;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onPlaceOffer() {
        // data validation is done in the trade domain
        openOfferManager.onPlaceOffer(offerId,
                direction,
                priceAsFiat.get(),
                amountAsCoin.get(),
                minAmountAsCoin.get(),
                (transaction) -> {
                    transactionId.set(transaction.getHashAsString());
                    requestPlaceOfferSuccess.set(true);
                },
                requestPlaceOfferErrorMessage::set
        );
    }

    void onSecurityDepositInfoDisplayed() {
        preferences.setDisplaySecurityDepositInfo(false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmountAsCoin.get() != null && amountAsCoin.get() != null)
            return !minAmountAsCoin.get().isGreaterThan(amountAsCoin.get());
        return true;
    }

    Offer.Direction getDirection() {
        return direction;
    }

    WalletService getWalletService() {
        return walletService;
    }

    String getOfferId() {
        return offerId;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }

    boolean getDisplaySecurityDepositInfo() {
        return preferences.getDisplaySecurityDepositInfo();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (priceAsFiat.get() != null &&
                amountAsCoin.get() != null &&
                !amountAsCoin.get().isZero() &&
                !priceAsFiat.get().isZero()) {
            volumeAsFiat.set(new ExchangeRate(priceAsFiat.get()).coinToFiat(amountAsCoin.get()));
        }
    }

    void calculateAmount() {
        if (volumeAsFiat.get() != null &&
                priceAsFiat.get() != null &&
                !volumeAsFiat.get().isZero() &&
                !priceAsFiat.get().isZero()) {
            // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
            amountAsCoin.set(formatter.reduceTo4Decimals(new ExchangeRate(priceAsFiat.get()).fiatToCoin(volumeAsFiat.get())));

            calculateTotalToPay();
        }
    }

    void calculateTotalToPay() {
        if (securityDepositAsCoin.get() != null) {
            if (direction == Offer.Direction.BUY)
                totalToPayAsCoin.set(offerFeeAsCoin.get().add(networkFeeAsCoin.get()).add(securityDepositAsCoin.get()));
            else
                totalToPayAsCoin.set(offerFeeAsCoin.get().add(networkFeeAsCoin.get()).add(securityDepositAsCoin.get()).add(amountAsCoin.get()));
        }
    }

    private void updateBalance(Coin balance) {
        isWalletFunded.set(totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0);
    }

    private void applyBankAccount(FiatAccount fiatAccount) {
        if (fiatAccount != null) {
            bankAccountType.set(fiatAccount.type.toString());
            bankAccountCurrency.set(fiatAccount.currencyCode);
            bankAccountCounty.set(fiatAccount.country.name);

            fiatCode.set(fiatAccount.currencyCode);
        }
    }
}
