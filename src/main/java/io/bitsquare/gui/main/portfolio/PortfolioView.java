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

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.trade.TradeManager;

import javax.inject.Inject;

import viewfx.Activatable;
import viewfx.ActivatableViewAndModel;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

class PortfolioView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML Tab offersTab, openTradesTab, closedTradesTab;

    private Tab currentTab;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final TradeManager tradeManager;

    @Inject
    public PortfolioView(ViewLoader viewLoader, Navigation navigation, TradeManager tradeManager) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.tradeManager = tradeManager;
    }

    @Override
    public void initialize() {
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
    }

    @Override
    public void doActivate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (tradeManager.getPendingTrades().size() == 0)
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO, Navigation.Item.OFFERS);
        else
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.PORTFOLIO, Navigation.Item.PENDING_TRADES);
    }

    @Override
    public void doDeactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
        currentTab = null;
    }

    private void loadView(Navigation.Item navigationItem) {

        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl());
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
        currentTab.setContent(loaded.view);
        root.getSelectionModel().select(currentTab);
    }
}

