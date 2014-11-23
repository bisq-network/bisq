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

package io.bitsquare.gui.main.funds;

import io.bitsquare.gui.Activatable;
import io.bitsquare.gui.ActivatableViewAndModel;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.View;
import io.bitsquare.gui.ViewLoader;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class FundsView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML Tab withdrawalTab, transactionsTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;
    private Tab currentTab;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    FundsView(ViewLoader viewLoader, Navigation navigation) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        navigationListener = navigationItems -> {
            if (navigationItems != null && navigationItems.length == 3
                    && navigationItems[1] == Navigation.Item.FUNDS)
                loadView(navigationItems[2]);
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == withdrawalTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.FUNDS, Navigation.Item.WITHDRAWAL);
            else if (newValue == transactionsTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.FUNDS, Navigation.Item.TRANSACTIONS);
        };
    }

    @Override
    public void doActivate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

        if (root.getSelectionModel().getSelectedItem() == transactionsTab)
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.FUNDS, Navigation.Item.TRANSACTIONS);
        else
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.FUNDS, Navigation.Item.WITHDRAWAL);
    }

    @Override
    public void doDeactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
    }

    @Override
    protected View loadView(Navigation.Item navigationItem) {
        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl());
        switch (navigationItem) {
            case WITHDRAWAL:
                currentTab = withdrawalTab;
                break;
            case TRANSACTIONS:
                currentTab = transactionsTab;
                break;
        }
        currentTab.setContent(loaded.view);
        root.getSelectionModel().select(currentTab);
        return (View) loaded.controller;
    }
}

