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

package io.bitsquare.offer;

import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.gui.main.trade.offerbook.OfferBookListItem;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.trade.TradeManager;
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
 * Holds and manages the unsorted and unfiltered offerbook list of both buy and sell offers.
 * It is handled as singleton by Guice and is used by 2 instances of OfferBookDataModel (one for Buy one for Sell).
 * As it is used only by the Buy and Sell UIs we treat it as local UI model.
 * It also use OfferRepository.Listener as the lists items class and we don't want to get any dependency out of the
 * package for that.
 */
public class OfferBook {

    private static final Logger log = LoggerFactory.getLogger(OfferBook.class);
    private static final int POLLING_INTERVAL = 1000;

    private final OfferBookService offerBookService;
    private final User user;

    private final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
    private final OfferBookService.Listener offerBookServiceListener;
    private final ChangeListener<FiatAccount> bankAccountChangeListener;
    private final ChangeListener<Number> invalidationListener;
    private String fiatCode;
    private AnimationTimer pollingTimer;
    private Country country;
    private int numClients = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBook(OfferBookService offerBookService, User user, TradeManager tradeManager) {
        this.offerBookService = offerBookService;
        this.user = user;

        bankAccountChangeListener = (observableValue, oldValue, newValue) -> setBankAccount(newValue);
        invalidationListener = (ov, oldValue, newValue) -> requestGetOffers();

        offerBookServiceListener = new OfferBookService.Listener() {
            @Override
            public void onOfferAdded(Offer offer) {
                addOfferToOfferBookListItems(offer);
            }

            @Override
            public void onOffersReceived(List<Offer> offers) {
                //TODO use deltas instead replacing the whole list
                offerBookListItems.clear();
                offers.stream().forEach(e -> addOfferToOfferBookListItems(e));
            }

            @Override
            public void onOfferRemoved(Offer offer) {
                // Update state in case that that offer is used in the take offer screen, so it gets updated correctly
                offer.setState(Offer.State.REMOVED);

                // clean up possible references in tradeManager 
                tradeManager.onOfferRemovedFromRemoteOfferBook(offer);

                offerBookListItems.removeIf(item -> item.getOffer().getId().equals(offer.getId()));
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addClient() {
        numClients++;
        if (numClients == 1)
            startPolling();
    }

    public void removeClient() {
        numClients--;
        checkArgument(numClients >= 0);
        if (numClients == 0)
            stopPolling();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setBankAccount(FiatAccount fiatAccount) {
        log.debug("setBankAccount " + fiatAccount);
        if (fiatAccount != null) {
            country = fiatAccount.getCountry();
            fiatCode = fiatAccount.getCurrency().getCurrencyCode();

            // TODO check why that was used (probably just for update triggering, if so refactor that)
            //offerBookListItems.stream().forEach(e -> e.setBankAccountCountry(country));
        }
        else {
            fiatCode = CurrencyUtil.getDefaultCurrency().getCurrencyCode();
        }
    }

    private void addListeners() {
        log.debug("addListeners ");
        user.currentFiatAccountProperty().addListener(bankAccountChangeListener);
        offerBookService.addListener(offerBookServiceListener);
        offerBookService.invalidationTimestampProperty().addListener(invalidationListener);
    }

    private void removeListeners() {
        log.debug("removeListeners ");
        user.currentFiatAccountProperty().removeListener(bankAccountChangeListener);
        offerBookService.removeListener(offerBookServiceListener);
        offerBookService.invalidationTimestampProperty().removeListener(invalidationListener);
    }

    private void addOfferToOfferBookListItems(Offer offer) {
        if (offer != null) {
            offerBookListItems.add(new OfferBookListItem(offer, country));
        }
    }

    private void requestGetOffers() {
        offerBookService.getOffers(fiatCode);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Polling
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO Just temporary, will be removed later when we have a push solution
    private void startPolling() {
        addListeners();
        setBankAccount(user.currentFiatAccountProperty().get());
        pollingTimer = Utilities.setInterval(POLLING_INTERVAL, (animationTimer) -> {
            offerBookService.requestInvalidationTimeStampFromDHT(fiatCode);
            return null;
        });

        offerBookService.getOffers(fiatCode);
    }

    private void stopPolling() {
        pollingTimer.stop();
        removeListeners();
    }

}
