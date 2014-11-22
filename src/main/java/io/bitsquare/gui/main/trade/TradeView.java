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

package io.bitsquare.gui.main.trade;

import io.bitsquare.gui.ActivatableView;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.trade.createoffer.CreateOfferView;
import io.bitsquare.gui.main.trade.offerbook.OfferBookView;
import io.bitsquare.gui.main.trade.takeoffer.TakeOfferView;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.net.URL;

import java.util.List;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeView extends ActivatableView implements TradeNavigator {
    private static final Logger log = LoggerFactory.getLogger(TradeView.class);

    private OfferBookView offerBookViewCB;
    private CreateOfferView createOfferViewCB;
    private TakeOfferView takeOfferViewCB;
    private Node createOfferView;
    private Node takeOfferView;
    private Navigation.Listener listener;
    private Navigation.Item navigationItem;
    private Direction direction;
    private Coin amount;
    private Fiat price;
    private Offer offer;

    private final ViewLoader viewLoader;
    private final Navigation navigation;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TradeView(ViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        direction = (this instanceof BuyView) ? Direction.BUY : Direction.SELL;
        navigationItem = (direction == Direction.BUY) ? Navigation.Item.BUY : Navigation.Item.SELL;

        listener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3 && navigationItems[1] == navigationItem) {
                loadView(navigationItems[2]);
            }
        };

        super.initialize(url, rb);
    }

    @Override
    public void doActivate() {
        // We need to remove open validation error popups
        // Platform.runLater needed as focus-out event is called after selectedIndexProperty changed
        // TODO Find a way to do that in the InputTextField directly, but a tab change does not trigger any event...
        TabPane tabPane = (TabPane) root;
        tabPane.getSelectionModel().selectedIndexProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        Platform.runLater(InputTextField::hideErrorMessageDisplay));

        // We want to get informed when a tab get closed
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1) {
                if (removedTabs.get(0).getContent().equals(createOfferView))
                    onCreateOfferViewRemoved();
                else if (removedTabs.get(0).getContent().equals(takeOfferView))
                    onTakeOfferViewRemoved();
            }
        });

        navigation.addListener(listener);
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.OFFER_BOOK);
    }

    @Override
    public void doDeactivate() {
        navigation.removeListener(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeNavigator implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void createOffer(Coin amount, Fiat price) {
        this.amount = amount;
        this.price = price;
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem,
                Navigation.Item.CREATE_OFFER);
    }

    @Override
    public void takeOffer(Coin amount, Fiat price, Offer offer) {
        this.amount = amount;
        this.price = price;
        this.offer = offer;
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem,
                Navigation.Item.TAKE_OFFER);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        super.loadView(navigationItem);
        TabPane tabPane = (TabPane) root;
        if (navigationItem == Navigation.Item.OFFER_BOOK && offerBookViewCB == null) {
            // Offerbook must not be cached by ViewLoader as we use 2 instances for sell and buy screens.
            ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl(), false);
            final Tab tab = new Tab(direction == Direction.BUY ? "Buy Bitcoin" : "Sell Bitcoin");
            tab.setClosable(false);
            tab.setContent(loaded.view);
            tabPane.getTabs().add(tab);
            offerBookViewCB = (OfferBookView) loaded.controller;
            offerBookViewCB.setParent(this);

            offerBookViewCB.setDirection(direction);
            // offerBookViewCB.setNavigationListener(n -> loadView(n));

            return offerBookViewCB;
        }
        else if (navigationItem == Navigation.Item.CREATE_OFFER && createOfferViewCB == null) {
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl(), false);
            createOfferView = loaded.view;
            createOfferViewCB = (CreateOfferView) loaded.controller;
            createOfferViewCB.setParent(this);
            createOfferViewCB.initWithData(direction, amount, price);
            final Tab tab = new Tab("Create offer");
            createOfferViewCB.configCloseHandlers(tab.closableProperty());
            tab.setContent(createOfferView);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            return createOfferViewCB;
        }
        else if (navigationItem == Navigation.Item.TAKE_OFFER && takeOfferViewCB == null &&
                offer != null) {
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            ViewLoader.Item loaded = viewLoader.load(Navigation.Item.TAKE_OFFER.getFxmlUrl(), false);
            takeOfferView = loaded.view;
            takeOfferViewCB = (TakeOfferView) loaded.controller;
            takeOfferViewCB.setParent(this);
            takeOfferViewCB.initWithData(direction, amount, offer);
            final Tab tab = new Tab("Take offer");
            takeOfferViewCB.configCloseHandlers(tab.closableProperty());
            tab.setContent(takeOfferView);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            return takeOfferViewCB;
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onCreateOfferViewRemoved() {
        createOfferViewCB = null;
        offerBookViewCB.enableCreateOfferButton();

        // update the navigation state
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.OFFER_BOOK);
    }

    private void onTakeOfferViewRemoved() {
        takeOfferViewCB = null;

        // update the navigation state
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.OFFER_BOOK);
    }

}

