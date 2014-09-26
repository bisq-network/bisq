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

package io.bitsquare.gui.main.trade.orderbook;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OrderBookListener;
import io.bitsquare.trade.Offer;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;

import java.util.List;

import javax.inject.Inject;

import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Holds and manages the unsorted and unfiltered orderbook list of both buy and sell offers.
 * It is handled as singleton by Guice and is used by 2 instances of OrderBookModel (one for Buy one for Sell).
 * As it is used only by the Buy and Sell UIs we treat it as local UI model.
 * It also use OrderBookListener as the lists items class and we don't want to get any dependency out of the package
 * for that.
 */
public class OrderBook {

    private static final Logger log = LoggerFactory.getLogger(OrderBook.class);

    private final MessageFacade messageFacade;
    private final User user;

    private final ObservableList<OrderBookListItem> orderBookListItems = FXCollections.observableArrayList();
    private final OrderBookListener orderBookListener;
    private final ChangeListener<BankAccount> bankAccountChangeListener;
    private final ChangeListener<Number> invalidationListener;
    private String fiatCode;
    private AnimationTimer pollingTimer;
    private Country country;
    private int numClients = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OrderBook(MessageFacade messageFacade, User user) {
        this.messageFacade = messageFacade;
        this.user = user;

        bankAccountChangeListener = (observableValue, oldValue, newValue) -> setBankAccount(newValue);
        invalidationListener = (ov, oldValue, newValue) -> requestOffers();

        orderBookListener = new OrderBookListener() {
            @Override
            public void onOfferAdded(Offer offer) {
                addOfferToOrderBookListItems(offer);
            }

            @Override
            public void onOffersReceived(List<Offer> offers) {
                //TODO use deltas instead replacing the whole list
                orderBookListItems.clear();
                offers.stream().forEach(e -> addOfferToOrderBookListItems(e));
            }

            @Override
            public void onOfferRemoved(Offer offer) {
                orderBookListItems.removeIf(item -> item.getOffer().getId().equals(offer.getId()));
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addClient() {
        numClients++;
        if (numClients == 1)
            startPolling();
    }

    void removeClient() {
        numClients--;
        checkArgument(numClients >= 0);
        if (numClients == 0)
            stopPolling();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    ObservableList<OrderBookListItem> getOrderBookListItems() {
        return orderBookListItems;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setBankAccount(BankAccount bankAccount) {
        log.debug("setBankAccount " + bankAccount);
        if (bankAccount != null) {
            country = bankAccount.getCountry();
            fiatCode = bankAccount.getCurrency().getCurrencyCode();
            orderBookListItems.stream().forEach(e -> e.setBankAccountCountry(country));
        }
        else {
            fiatCode = CurrencyUtil.getDefaultCurrency().getCurrencyCode();
        }
    }

    private void addListeners() {
        log.debug("addListeners ");
        user.currentBankAccountProperty().addListener(bankAccountChangeListener);
        messageFacade.addOrderBookListener(orderBookListener);
        messageFacade.invalidationTimestampProperty().addListener(invalidationListener);
    }

    private void removeListeners() {
        log.debug("removeListeners ");
        user.currentBankAccountProperty().removeListener(bankAccountChangeListener);
        messageFacade.removeOrderBookListener(orderBookListener);
        messageFacade.invalidationTimestampProperty().removeListener(invalidationListener);
    }

    private void addOfferToOrderBookListItems(Offer offer) {
        if (offer != null) {
            orderBookListItems.add(new OrderBookListItem(offer, country));
        }
    }

    private void requestOffers() {
        messageFacade.getOffers(fiatCode);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Polling
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO Just temporary, will be removed later when we have a push solution
    private void startPolling() {
        addListeners();
        setBankAccount(user.getCurrentBankAccount());
        pollingTimer = Utilities.setInterval(1000, (animationTimer) -> {
            messageFacade.requestInvalidationTimeStampFromDHT(fiatCode);
            return null;
        });

        messageFacade.getOffers(fiatCode);
    }

    private void stopPolling() {
        pollingTimer.stop();
        removeListeners();
    }

}
