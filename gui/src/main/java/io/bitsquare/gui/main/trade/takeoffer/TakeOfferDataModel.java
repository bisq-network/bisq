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

package io.bitsquare.gui.main.trade.takeoffer;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.offer.Offer;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Preferences;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import viewfx.model.Activatable;
import viewfx.model.DataModel;

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
    private final Persistence persistence;

    private Offer offer;
    private AddressEntry addressEntry;

    final StringProperty requestTakeOfferErrorMessage = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty requestTakeOfferSuccess = new SimpleBooleanProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty useMBTC = new SimpleBooleanProperty();

    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> securityDepositAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> offerFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> networkFeeAsCoin = new SimpleObjectProperty<>();

    final ObjectProperty<Offer.State> offerIsAvailable = new SimpleObjectProperty<>(Offer.State.UNKNOWN);

    @Inject
    public TakeOfferDataModel(TradeManager tradeManager,
                              WalletService walletService,
                              Preferences preferences,
                              Persistence persistence) {
        this.tradeManager = tradeManager;
        this.walletService = walletService;
        this.preferences = preferences;
        this.persistence = persistence;

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
        tradeManager.stopRequestIsOfferAvailableRequest(offer);
    }

    void initWithData(Coin amount, Offer offer) {
        this.offer = offer;

        if (amount != null &&
                !amount.isGreaterThan(offer.getAmount()) &&
                !offer.getMinAmount().isGreaterThan(amount)) {
            amountAsCoin.set(amount);
        }
        else {
            amountAsCoin.set(offer.getAmount());
        }

        securityDepositAsCoin.set(offer.getSecurityDeposit());
        calculateVolume();
        calculateTotalToPay();

        addressEntry = walletService.getAddressInfoByTradeID(offer.getId());
        walletService.addBalanceListener(new BalanceListener(addressEntry.getAddress()) {
            @Override
            public void onBalanceChanged(@NotNull Coin balance) {
                updateBalance(balance);
            }
        });
        updateBalance(walletService.getBalanceForAddress(addressEntry.getAddress()));

        offer.getStateProperty().addListener((observable, oldValue, newValue) -> {
            offerIsAvailable.set(newValue);
        });
        tradeManager.requestIsOfferAvailable(offer);
    }

    void takeOffer() {
        final Trade trade = tradeManager.takeOffer(amountAsCoin.get(), offer);
        trade.stateProperty().addListener((ov, oldValue, newValue) -> {
            log.debug("trade state = " + newValue);
            switch (newValue) {
                // TODO Check why DEPOSIT_CONFIRMED can happen, refactor state handling
                case DEPOSIT_PUBLISHED:
                case DEPOSIT_CONFIRMED:
                    // TODO null pointer happened here!
                    if (trade.getDepositTx() != null) {
                        transactionId.set(trade.getDepositTx().getHashAsString());
                        requestTakeOfferSuccess.set(true);
                    }
                    else {
                        log.warn("trade.getDepositTx() = null. at trade state " + newValue +
                                " That should not happen and needs more investigation why it can happen.");
                    }
                    break;
                case FAILED:
                    requestTakeOfferErrorMessage.set("An error occurred. Error: " + trade.getFault().getMessage());
                    break;
                case OFFERER_REJECTED:
                    requestTakeOfferErrorMessage.set("Take offer request got rejected.");
                    break;
                default:
                    log.warn("Unhandled trade state: " + newValue);
                    break;
            }
        });
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
        try {
            if (securityDepositAsCoin.get() != null) {
                totalToPayAsCoin.set(offerFeeAsCoin.get().add(amountAsCoin.get()).add(networkFeeAsCoin.get()).add
                        (securityDepositAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
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

    Boolean displaySecurityDepositInfo() {
        Object securityDepositInfoDisplayedObject = persistence.read("displaySecurityDepositInfo");
        if (securityDepositInfoDisplayedObject instanceof Boolean)
            return (Boolean) securityDepositInfoDisplayedObject;
        else
            return true;
    }

    void securityDepositInfoDisplayed() {
        persistence.write("displaySecurityDepositInfo", false);
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
