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

package io.bitsquare.gui.main.orders;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrdersViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(OrdersViewCB.class);

    private final Navigation navigation;
    private final TradeManager tradeManager;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @FXML Tab offersTab, pendingTradesTab, closedTradesTab;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OrdersViewCB(Navigation navigation, TradeManager tradeManager) {
        super();

        this.navigation = navigation;
        this.tradeManager = tradeManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        navigationListener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3
                    && navigationItems[1] == Navigation.Item.ORDERS)
                loadView(navigationItems[2]);
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == offersTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS, Navigation.Item.OFFERS);
            else if (newValue == pendingTradesTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS, Navigation.Item.PENDING_TRADES);
            else if (newValue == closedTradesTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS, Navigation.Item.CLOSED_TRADES);
        };

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (tradeManager.getPendingTrades().size() == 0)
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS, Navigation.Item.OFFERS);
        else
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS, Navigation.Item.PENDING_TRADES);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        super.loadView(navigationItem);

        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            Parent view = loader.load();
            Tab tab = null;
            switch (navigationItem) {
                case OFFERS:
                    tab = offersTab;
                    break;
                case PENDING_TRADES:
                    tab = pendingTradesTab;
                    break;
                case CLOSED_TRADES:
                    tab = closedTradesTab;
                    break;
            }
            tab.setContent(view);
            ((TabPane) root).getSelectionModel().select(tab);
            Initializable childController = loader.getController();
            ((ViewCB) childController).setParent(this);

        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.printStackTrace();
        }
        return childController;
    }
}

