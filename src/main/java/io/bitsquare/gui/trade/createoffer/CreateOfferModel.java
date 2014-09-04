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

package io.bitsquare.gui.trade.createoffer;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.UIModel;
import io.bitsquare.locale.Country;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.ExchangeRate;
import com.google.bitcoin.utils.Fiat;

import com.google.inject.Inject;

import java.util.Locale;
import java.util.UUID;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static io.bitsquare.gui.util.BSFormatter.reduceTo4Decimals;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferModel.class);

    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    private final Settings settings;
    private final User user;

    private final String offerId;
    @Nullable private Direction direction = null;

    AddressEntry addressEntry;

    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();

    final LongProperty collateralAsLong = new SimpleLongProperty();

    final BooleanProperty requestPlaceOfferSuccess = new SimpleBooleanProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();

    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> minAmountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> collateralAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> offerFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> networkFeeAsCoin = new SimpleObjectProperty<>();

    final ObservableList<Country> acceptedCountries = FXCollections.observableArrayList();
    final ObservableList<Locale> acceptedLanguages = FXCollections.observableArrayList();
    final ObservableList<Arbitrator> acceptedArbitrators = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    CreateOfferModel(TradeManager tradeManager, WalletFacade walletFacade, Settings settings, User user) {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;
        this.settings = settings;
        this.user = user;

        offerId = UUID.randomUUID().toString();

        // Node: Don't do setup in constructor to make object creation faster
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialized() {
        super.initialized();

        // static data
        offerFeeAsCoin.set(FeePolicy.CREATE_OFFER_FEE);
        networkFeeAsCoin.set(FeePolicy.TX_FEE);

        if (walletFacade != null && walletFacade.getWallet() != null) {
            addressEntry = walletFacade.getAddressInfoByTradeID(offerId);

            walletFacade.addBalanceListener(new BalanceListener(addressEntry.getAddress()) {
                @Override
                public void onBalanceChanged(@NotNull Coin balance) {
                    updateBalance(balance);
                }
            });
            updateBalance(walletFacade.getBalanceForAddress(addressEntry.getAddress()));
        }
    }

    @Override
    public void activate() {
        super.activate();

        // might be changed after screen change
        if (settings != null) {
            collateralAsLong.set(settings.getCollateral());
            acceptedCountries.setAll(settings.getAcceptedCountries());
            acceptedLanguages.setAll(settings.getAcceptedLanguageLocales());
            acceptedArbitrators.setAll(settings.getAcceptedArbitrators());
        }

        if (user != null) {
            BankAccount bankAccount = user.getCurrentBankAccount();
            if (bankAccount != null) {
                bankAccountType.set(bankAccount.getBankAccountType().toString());
                bankAccountCurrency.set(bankAccount.getCurrency().getCurrencyCode());
                bankAccountCounty.set(bankAccount.getCountry().getName());
            }
        }
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void placeOffer() {
        // data validation is done in the trade domain
        tradeManager.requestPlaceOffer(offerId,
                direction,
                priceAsFiat.get(),
                amountAsCoin.get(),
                minAmountAsCoin.get(),
                (transaction) -> {
                    transactionId.set(transaction.getHashAsString());
                    requestPlaceOfferSuccess.set(true);
                },
                (errorMessage) -> requestPlaceOfferErrorMessage.set(errorMessage)
        );
    }

    void calculateVolume() {
        try {
            if (priceAsFiat.get() != null && amountAsCoin.get() != null && !amountAsCoin.get().isZero() && !priceAsFiat
                    .get().isZero()) {
                volumeAsFiat.set(new ExchangeRate(priceAsFiat.get()).coinToFiat(amountAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateAmount() {
        try {
            if (volumeAsFiat.get() != null && priceAsFiat.get() != null && !volumeAsFiat.get().isZero() && !priceAsFiat
                    .get().isZero()) {
                amountAsCoin.set(new ExchangeRate(priceAsFiat.get()).fiatToCoin(volumeAsFiat.get()));

                // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
                amountAsCoin.set(reduceTo4Decimals(amountAsCoin.get()));
                calculateTotalToPay();
                calculateCollateral();
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateTotalToPay() {
        calculateCollateral();
        try {
            if (collateralAsCoin.get() != null) {
                totalToPayAsCoin.set(offerFeeAsCoin.get().add(networkFeeAsCoin.get()).add(collateralAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateCollateral() {
        try {

            if (amountAsCoin.get() != null)
                collateralAsCoin.set(amountAsCoin.get().multiply(collateralAsLong.get()).divide(1000L));
        } catch (Throwable t) {
            // The multiply might lead to too large numbers, we don't handle it but it should not break the app
            log.error(t.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Validation
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmountAsCoin.get() != null && amountAsCoin.get() != null)
            return !minAmountAsCoin.get().isGreaterThan(amountAsCoin.get());
        return true;
    }

    private void updateBalance(@NotNull Coin balance) {
        isWalletFunded.set(totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter/Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    Direction getDirection() {
        return direction;
    }

    void setDirection(Direction direction) {
        // direction can not be changed once it is initially set
        checkArgument(this.direction == null);
        this.direction = direction;
    }

    public WalletFacade getWalletFacade() {
        return walletFacade;
    }

    String getOfferId() {
        return offerId;
    }

}
