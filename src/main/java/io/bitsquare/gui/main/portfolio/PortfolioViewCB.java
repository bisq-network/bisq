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

package io.bitsquare.gui.main.portfolio;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.trade.TradeManager;

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

public class PortfolioViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(PortfolioViewCB.class);

    private final Navigation navigation;
    private final TradeManager tradeManager;

    private Tab currentTab;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @FXML Tab offersTab, openTradesTab, closedTradesTab;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    PortfolioViewCB(Navigation navigation, TradeManager tradeManager) {
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
                    && navigationItems[1] == Navigation.Item.PORTFOLIO)
                loadView(navigationItems[2]);
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == offersTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO, Navigation.Item.OFFERS);
            else if (newValue == openTradesTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO,
                        Navigation.Item.PENDING_TRADES);
            else if (newValue == closedTradesTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO, Navigation.Item.CLOSED_TRADES);
        };

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (tradeManager.getPendingTrades().size() == 0)
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO, Navigation.Item.OFFERS);
        else
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO, Navigation.Item.PENDING_TRADES);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        ((TabPane) root).getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
        currentTab = null;
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
        
        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        final ViewLoader loader = new ViewLoader(navigationItem);
        Node view = loader.load();
        switch (navigationItem) {
            case OFFERS:
                currentTab = offersTab;
                break;
            case PENDING_TRADES:
                currentTab = openTradesTab;
                break;
            case CLOSED_TRADES:
                currentTab = closedTradesTab;
                break;
        }
        currentTab.setContent(view);
        ((TabPane) root).getSelectionModel().select(currentTab);
        Initializable childController = loader.getController();
        ((ViewCB) childController).setParent(this);

        return childController;
    }
}

