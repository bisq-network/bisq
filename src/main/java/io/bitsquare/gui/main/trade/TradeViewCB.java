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
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.trade.createoffer.CreateOfferViewCB;
import io.bitsquare.gui.main.trade.orderbook.OrderBookViewCB;
import io.bitsquare.gui.main.trade.takeoffer.TakeOfferController;
import io.bitsquare.util.BSFXMLLoader;

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

import static com.google.common.base.Preconditions.checkArgument;

public abstract class TradeViewCB extends CachedViewCB<TradePM> {
    private static final Logger log = LoggerFactory.getLogger(TradeViewCB.class);

    protected OrderBookViewCB orderBookViewCB;
    private CreateOfferViewCB createOfferViewCB;
    private TakeOfferController takeOfferController;
    private Node createOfferView;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TradeViewCB(TradePM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        loadView(NavigationItem.ORDER_BOOK);
        initOrderBook();
    }

    @Override
    public void activate() {
        super.activate();

        // We need to remove open validation error popups
        // TODO Find a way to do that in the InputTextField directly, but a tab change does not trigger any event there
        // Platform.runLater needed as focusout evetn is called after selectedIndexProperty changed
        TabPane tabPane = (TabPane) root;
        tabPane.getSelectionModel().selectedIndexProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        Platform.runLater(InputTextField::hideErrorMessageDisplay));

        // We want to get informed when a tab get closed
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1 && removedTabs.get(0).getContent().equals(createOfferView)) {
                if (createOfferViewCB != null) {
                    createOfferViewCB.terminate();
                    createOfferViewCB = null;
                }
            }
        });
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
    public Initializable loadView(NavigationItem navigationItem) {
        super.loadView(navigationItem);

        TabPane tabPane = (TabPane) root;
        if (navigationItem == NavigationItem.ORDER_BOOK) {
            checkArgument(orderBookViewCB == null);
            // Orderbook must not be cached by GuiceFXMLLoader as we use 2 instances for sell and buy screens.
            BSFXMLLoader orderBookLoader =
                    new BSFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
            try {
                final Parent view = orderBookLoader.load();
                final Tab tab = new Tab("Orderbook");
                tab.setClosable(false);
                tab.setContent(view);
                tabPane.getTabs().add(tab);
                orderBookViewCB = orderBookLoader.getController();
                orderBookViewCB.setParent(this);
                return orderBookViewCB;
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        else if (navigationItem == NavigationItem.CREATE_OFFER) {
            checkArgument(createOfferViewCB == null);

            // CreateOffer and TakeOffer must not be cached by GuiceFXMLLoader as we cannot use a view multiple times
            // in different graphs
            BSFXMLLoader loader = new BSFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
            try {
                createOfferView = loader.load();
                createOfferViewCB = loader.getController();
                createOfferViewCB.setParent(this);
                createOfferViewCB.setOnClose(() -> {
                    orderBookViewCB.enableCreateOfferButton();
                    return null;
                });

                final Tab tab = new Tab("Create offer");
                tab.setContent(createOfferView);
                tabPane.getTabs().add(tab);
                tabPane.getSelectionModel().select(tab);
                return createOfferViewCB;
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        else if (navigationItem == NavigationItem.TAKE_OFFER) {
            checkArgument(takeOfferController == null);

            // CreateOffer and TakeOffer must not be cached by GuiceFXMLLoader as we cannot use a view multiple times
            // in different graphs
            BSFXMLLoader loader = new BSFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
            try {
                final Parent view = loader.load();
                takeOfferController = loader.getController();
                takeOfferController.setParentController(this);
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

    public void onCreateOfferViewRemoved() {
        createOfferViewCB = null;
        orderBookViewCB.enableCreateOfferButton();
    }

    public void onTakeOfferViewRemoved() {
        takeOfferController = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract protected void initOrderBook();


}

