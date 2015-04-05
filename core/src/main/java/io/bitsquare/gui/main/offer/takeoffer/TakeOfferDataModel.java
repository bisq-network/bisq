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

package io.bitsquare.gui.main.offer.takeoffer;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.handlers.TakeOfferResultHandler;
import io.bitsquare.user.Preferences;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class TakeOfferDataModel implements Activatable, DataModel {
    private static final Logger log = LoggerFactory.getLogger(TakeOfferDataModel.class);

    private final TradeManager tradeManager;
    private final WalletService walletService;
    private final Preferences preferences;

    private Offer offer;
    private AddressEntry addressEntry;

    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty useMBTC = new SimpleBooleanProperty();

    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> securityDepositAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> offerFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> networkFeeAsCoin = new SimpleObjectProperty<>();

    @Inject
    public TakeOfferDataModel(TradeManager tradeManager,
                              WalletService walletService,
                              Preferences preferences) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.preferences = preferences;

        offerFeeAsCoin.set(FeePolicy.CREATE_OFFER_FEE);
        networkFeeAsCoin.set(FeePolicy.TX_FEE);
    }

    @Override
    public void activate() {
        btcCode.bind(preferences.btcDenominationProperty());
    }

    @Override
    public void deactivate() {
        btcCode.unbind();
        tradeManager.cancelCheckOfferAvailabilityRequest(offer);
    }

    void initWithData(Coin amount, Offer offer) {
        this.offer = offer;
        securityDepositAsCoin.set(offer.getSecurityDeposit());

        if (amount != null &&
                !amount.isGreaterThan(offer.getAmount()) &&
                !offer.getMinAmount().isGreaterThan(amount)) {
            amountAsCoin.set(amount);
        }
        else {
            amountAsCoin.set(offer.getAmount());
        }

        calculateVolume();
        calculateTotalToPay();

        addressEntry = walletService.getAddressEntry(offer.getId());
        walletService.addBalanceListener(new BalanceListener(addressEntry.getAddress()) {
            @Override
            public void onBalanceChanged(@NotNull Coin balance) {
                updateBalance(balance);
            }
        });
        updateBalance(walletService.getBalanceForAddress(addressEntry.getAddress()));

        tradeManager.checkOfferAvailability(offer);
    }

    void takeOffer(TakeOfferResultHandler handler) {
        tradeManager.requestTakeOffer(amountAsCoin.get(), offer, handler::handleResult);
    }

    void calculateVolume() {
        try {
            if (offer != null &&
                    offer.getPrice() != null &&
                    amountAsCoin.get() != null &&
                    !amountAsCoin.get().isZero()) {
                volumeAsFiat.set(new ExchangeRate(offer.getPrice()).coinToFiat(amountAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateTotalToPay() {
        if (getDirection() == Offer.Direction.SELL)
            totalToPayAsCoin.set(offerFeeAsCoin.get().add(networkFeeAsCoin.get()).add(securityDepositAsCoin.get()));
        else
            totalToPayAsCoin.set(offerFeeAsCoin.get().add(networkFeeAsCoin.get()).add(securityDepositAsCoin.get()).add(amountAsCoin.get()));
    }

    Offer.Direction getDirection() {
        return offer.getDirection();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (offer != null && offer.getMinAmount() != null && amountAsCoin.get() != null)
            return !offer.getMinAmount().isGreaterThan(amountAsCoin.get());
        return true;
    }

    boolean isAmountLargerThanOfferAmount() {
        //noinspection SimplifiableIfStatement
        if (amountAsCoin.get() != null && offer != null)
            return amountAsCoin.get().isGreaterThan(offer.getAmount());
        return true;
    }

    Boolean getDisplaySecurityDepositInfo() {
        return preferences.getDisplaySecurityDepositInfo();
    }

    void securityDepositInfoDisplayed() {
        preferences.setDisplaySecurityDepositInfo(false);
    }


    WalletService getWalletService() {
        return walletService;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }


    private void updateBalance(@NotNull Coin balance) {
        isWalletFunded.set(totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0);
    }

}
