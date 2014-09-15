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

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.trade.createoffer.CreateOfferViewCB;
import io.bitsquare.gui.main.trade.orderbook.OrderBookViewCB;
import io.bitsquare.gui.main.trade.takeoffer.TakeOfferController;
import io.bitsquare.trade.Direction;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

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

public class TradeViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(TradeViewCB.class);

    private final OrderBookInfo orderBookInfo = new OrderBookInfo();
    private OrderBookViewCB orderBookViewCB;
    private CreateOfferViewCB createOfferViewCB;
    private TakeOfferController takeOfferController;
    private Node createOfferView;
    private Navigation navigation;
    private Navigation.Listener listener;
    private Navigation.Item navigationItem;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TradeViewCB(Navigation navigation) {
        super();

        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        listener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3 && navigationItems[1] == navigationItem) {
                loadView(navigationItems[2]);
            }
        };

        Direction direction = (this instanceof BuyViewCB) ? Direction.BUY : Direction.SELL;
        orderBookInfo.setDirection(direction);
        navigationItem = (direction == Direction.BUY) ? Navigation.Item.BUY : Navigation.Item.SELL;

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        // We need to remove open validation error popups
        // TODO Find a way to do that in the InputTextField directly, but a tab change does not trigger any event
        // there
        // Platform.runLater needed as focus-out event is called after selectedIndexProperty changed
        TabPane tabPane = (TabPane) root;
        tabPane.getSelectionModel().selectedIndexProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        Platform.runLater(InputTextField::hideErrorMessageDisplay));

        // We want to get informed when a tab get closed
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1 && removedTabs.get(0).getContent().equals(createOfferView)) {
                onCreateOfferViewRemoved();
            }
        });

        navigation.addListener(listener);
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.ORDER_BOOK);
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
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // @Override

    protected Initializable loadView(Navigation.Item navigationItem) {
        super.loadView(navigationItem);
        TabPane tabPane = (TabPane) root;
        if (navigationItem == Navigation.Item.ORDER_BOOK && orderBookViewCB == null) {
            // Orderbook must not be cached by ViewLoader as we use 2 instances for sell and buy screens.
            ViewLoader orderBookLoader =
                    new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
            try {
                final Parent view = orderBookLoader.load();
                final Tab tab = new Tab("Orderbook");
                tab.setClosable(false);
                tab.setContent(view);
                tabPane.getTabs().add(tab);
                orderBookViewCB = orderBookLoader.getController();
                orderBookViewCB.setParent(this);
                orderBookViewCB.setOrderBookInfo(orderBookInfo);
                // orderBookViewCB.setNavigationListener(n -> loadView(n));

                return orderBookViewCB;
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        else if (navigationItem == Navigation.Item.CREATE_OFFER && createOfferViewCB == null) {
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
            try {
                createOfferView = loader.load();
                createOfferViewCB = loader.getController();
                createOfferViewCB.setParent(this);
                createOfferViewCB.initWithOrderBookInfo(orderBookInfo);
                createOfferViewCB.setCloseListener(() -> onCreateOfferViewRemoved());
                final Tab tab = new Tab("Create offer");
                tab.setContent(createOfferView);
                tabPane.getTabs().add(tab);
                tabPane.getSelectionModel().select(tab);
                return createOfferViewCB;
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        else if (navigationItem == Navigation.Item.TAKE_OFFER && takeOfferController == null) {
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
            try {
                final Parent view = loader.load();
                takeOfferController = loader.getController();
                takeOfferController.setParentController(this);
                takeOfferController.initWithData(orderBookInfo);
                final Tab tab = new Tab("Take offer");
                tab.setContent(view);
                tabPane.getTabs().add(tab);
                tabPane.getSelectionModel().select(tab);

                return takeOfferController;
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        else {
            log.error("navigationItem not supported: " + navigationItem);
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO takeOfferController is not updated yet to new UI pattern
    public void onTakeOfferViewRemoved() {
        takeOfferController = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onCreateOfferViewRemoved() {
        if (createOfferViewCB != null)
            createOfferViewCB = null;
        orderBookViewCB.enableCreateOfferButton();

        // update the navigation state
        navigation.navigationTo(Navigation.Item.MAIN, navigationItem, Navigation.Item.ORDER_BOOK);
    }
}

