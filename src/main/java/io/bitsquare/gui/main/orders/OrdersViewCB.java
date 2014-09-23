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
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrdersViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(OrdersViewCB.class);

    private Navigation navigation;
    private Navigation.Listener listener;

    @FXML Tab offersTab, pendingTradesTab, closedTradesTab;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OrdersViewCB(Navigation navigation) {
        super();

        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        listener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3 && navigationItems[1] == Navigation.Item.ORDERS)
                loadView(navigationItems[2]);
        };

        super.initialize(url, rb);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();

        navigation.addListener(listener);
        navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ORDERS, Navigation.Item.PENDING_TRADES);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();

        navigation.removeListener(listener);
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
            GridPane view = loader.load();
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
            log.error("Loading view failed. FxmlUrl = " + Navigation.Item.ACCOUNT_SETUP.getFxmlUrl());
            e.printStackTrace();
        }
        return childController;
    }


}

