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

package io.bitsquare.msg.dhttest;

import io.bitsquare.BitSquare;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.main.trade.orderbook.OrderBookListItem;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.msg.BootstrappedPeerFactory;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.AddOfferListener;
import io.bitsquare.msg.listeners.BootstrapListener;
import io.bitsquare.msg.listeners.OrderBookListener;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;

import java.net.URL;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DHTTestController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(DHTTestController.class);

    private final MessageFacade messageFacade;
    private BootstrappedPeerFactory bootstrappedPeerFactory;
    private User user;
    private final ObservableList<OrderBookListItem> orderBookListItems = FXCollections.observableArrayList();

    @FXML TableView table;
    @FXML TextArea stateLabel;
    @FXML TextField toSaveTextField;
    @FXML TableColumn<OrderBookListItem, OrderBookListItem> idColumn;

    @Inject
    private DHTTestController(MessageFacade messageFacade, User user,
                              BootstrappedPeerFactory bootstrappedPeerFactory) {
        this.user = user;
        this.messageFacade = messageFacade;
        this.bootstrappedPeerFactory = bootstrappedPeerFactory;

        user.applyPersistedUser(null);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        messageFacade.init(BitSquare.getPort(), new BootstrapListener() {
            @Override
            public void onCompleted() {
                onMessageFacadeInitialised();
            }

            @Override
            public void onFailed(Throwable throwable) {
                log.error(throwable.toString());
            }
        });

        messageFacade.addOrderBookListener(new OrderBookListener() {
            @Override
            public void onOfferAdded(Offer offer) {
                log.debug("offer added " + offer.getId());
            }

            @Override
            public void onOffersReceived(List<Offer> offers) {
                //TODO use deltas instead replacing the whole list
                orderBookListItems.clear();
                offers.stream().forEach(offer -> {
                    if (offer != null) {
                        orderBookListItems.add(new OrderBookListItem(offer, CountryUtil.getDefaultCountry()));
                    }
                });
            }

            @Override
            public void onOfferRemoved(Offer offer) {
                orderBookListItems.removeIf(item -> item.getOffer().getId().equals(offer.getId()));
            }
        });

        setIDColumnCellFactory();
        table.setItems(orderBookListItems);

        bootstrappedPeerFactory.connectionState.addListener((ov, oldValue, newValue) -> {
            stateLabel.setText(newValue);
        });
    }

    @FXML
    public void onAddOffer() {
        Offer offer = new Offer(toSaveTextField.getText(),
                user.getMessagePublicKey(),
                Direction.BUY,
                500,
                Coin.COIN,
                Coin.COIN,
                BankAccountType.SEPA,
                Currency.getInstance("EUR"),
                CountryUtil.getDefaultCountry(),
                "bankAccountUID",
                new ArrayList<>(),
                Coin.parseCoin("0.1"),
                new ArrayList<>(),
                new ArrayList<>());

        messageFacade.addOffer(offer, new AddOfferListener() {
            @Override
            public void onComplete() {
                log.debug("onAddOffer onComplete");
            }

            @Override
            public void onFailed(String reason, Throwable throwable) {
                log.debug("onAddOffer onFailed");
            }
        });
    }

    public void onGetOffers() {
        log.debug("onLoad");
        messageFacade.getOffers("EUR");
    }

    private void onMessageFacadeInitialised() {
        log.debug("onMessageFacadeInitialised");
    }

    private void setIDColumnCellFactory() {
        idColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        idColumn.setCellFactory(
                new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem,
                        OrderBookListItem>>() {
                    @Override
                    public TableCell<OrderBookListItem, OrderBookListItem> call(
                            TableColumn<OrderBookListItem, OrderBookListItem> column) {
                        return new TableCell<OrderBookListItem, OrderBookListItem>() {
                            @Override
                            public void updateItem(final OrderBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && item.getOffer() != null && item.getOffer().getAmount() != null)
                                    setText(item.getOffer().getId());
                            }
                        };
                    }
                });
    }

}

