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
import io.bitsquare.gui.View;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.trade.createoffer.CreateOfferView;
import io.bitsquare.gui.main.trade.offerbook.OfferBookView;
import io.bitsquare.gui.main.trade.takeoffer.TakeOfferView;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.List;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.*;
import javafx.scene.control.*;

public class TradeView extends ActivatableView {

    private OfferBookView offerBookView;
    private CreateOfferView createOfferView;
    private TakeOfferView takeOfferView;
    private Node createOfferRoot;
    private Node takeOfferRoot;
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
    protected void initialize() {
        direction = (this instanceof BuyView) ? Direction.BUY : Direction.SELL;
        navigationItem = (direction == Direction.BUY) ? Navigation.Item.BUY : Navigation.Item.SELL;

        listener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3 && navigationItems[1] == navigationItem) {
                loadView(navigationItems[2]);
            }
        };
    }

    @Override
    protected void activate() {
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
                if (removedTabs.get(0).getContent().equals(createOfferRoot))
                    onCreateOfferViewRemoved();
                else if (removedTabs.get(0).getContent().equals(takeOfferRoot))
                    onTakeOfferViewRemoved();
            }
        });

        navigation.addListener(listener);
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.OFFER_BOOK);
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);
    }


    public void createOffer(Coin amount, Fiat price) {
        this.amount = amount;
        this.price = price;
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.CREATE_OFFER);
    }

    public void takeOffer(Coin amount, Fiat price, Offer offer) {
        this.amount = amount;
        this.price = price;
        this.offer = offer;
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.TAKE_OFFER);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected View loadView(Navigation.Item navigationItem) {
        TabPane tabPane = (TabPane) root;
        if (navigationItem == Navigation.Item.OFFER_BOOK && offerBookView == null) {
            // Offerbook must not be cached by ViewLoader as we use 2 instances for sell and buy screens.
            ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl(), false);
            final Tab tab = new Tab(direction == Direction.BUY ? "Buy Bitcoin" : "Sell Bitcoin");
            tab.setClosable(false);
            tab.setContent(loaded.view);
            tabPane.getTabs().add(tab);
            offerBookView = (OfferBookView) loaded.controller;
            offerBookView.setParent(this);

            offerBookView.setDirection(direction);

            return offerBookView;
        }
        else if (navigationItem == Navigation.Item.CREATE_OFFER && createOfferView == null) {
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl(), false);
            createOfferRoot = loaded.view;
            createOfferView = (CreateOfferView) loaded.controller;
            createOfferView.initWithData(direction, amount, price);
            final Tab tab = new Tab("Create offer");
            createOfferView.configCloseHandlers(tab.closableProperty());
            tab.setContent(createOfferRoot);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            return createOfferView;
        }
        else if (navigationItem == Navigation.Item.TAKE_OFFER && takeOfferView == null &&
                offer != null) {
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            ViewLoader.Item loaded = viewLoader.load(Navigation.Item.TAKE_OFFER.getFxmlUrl(), false);
            takeOfferRoot = loaded.view;
            takeOfferView = (TakeOfferView) loaded.controller;
            takeOfferView.initWithData(direction, amount, offer);
            final Tab tab = new Tab("Take offer");
            takeOfferView.configCloseHandlers(tab.closableProperty());
            tab.setContent(takeOfferRoot);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            return takeOfferView;
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
        createOfferView = null;
        offerBookView.enableCreateOfferButton();

        // update the navigation state
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.OFFER_BOOK);
    }

    private void onTakeOfferViewRemoved() {
        takeOfferView = null;

        // update the navigation state
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.OFFER_BOOK);
    }

}

