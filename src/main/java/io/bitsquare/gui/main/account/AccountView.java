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

package io.bitsquare.gui.main.account;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewLoader;

import javax.inject.Inject;

import viewfx.ActivatableView;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AccountView extends ActivatableView<TabPane, AccountViewModel> {

    @FXML Tab accountSettingsTab, arbitratorSettingsTab;

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    private AccountView(AccountViewModel model, ViewLoader viewLoader, Navigation navigation) {
        super(model);
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        navigationListener = navigationItems -> {
            if (navigationItems != null &&
                    navigationItems.length == 3 &&
                    navigationItems[1] == Navigation.Item.ACCOUNT)
                loadView(navigationItems[2]);
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == accountSettingsTab)
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                        Navigation.Item.ACCOUNT_SETTINGS);
            else
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                        Navigation.Item.ARBITRATOR_SETTINGS);

        };
    }

    @Override
    public void activate() {
        navigation.addListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

        if (navigation.getCurrentItems().length == 2 &&
                navigation.getCurrentItems()[1] == Navigation.Item.ACCOUNT) {
            if (model.getNeedRegistration()) {
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                        Navigation.Item.ACCOUNT_SETUP);
            }
            else {
                if (root.getSelectionModel().getSelectedItem() == accountSettingsTab)
                    navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                            Navigation.Item.ACCOUNT_SETTINGS);
                else
                    navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                            Navigation.Item.ARBITRATOR_SETTINGS);
            }
        }
    }

    @Override
    public void deactivate() {
        navigation.removeListener(navigationListener);
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
    }


    private void loadView(Navigation.Item navigationItem) {
        ViewLoader.Item loaded = viewLoader.load(navigationItem.getFxmlUrl());
        final Tab tab;
        switch (navigationItem) {
            case ACCOUNT_SETTINGS:
                tab = accountSettingsTab;
                tab.setText("Account settings");
                arbitratorSettingsTab.setDisable(false);
                break;
            case ACCOUNT_SETUP:
                tab = accountSettingsTab;
                tab.setText("Account setup");
                arbitratorSettingsTab.setDisable(true);
                break;
            case ARBITRATOR_SETTINGS:
                tab = arbitratorSettingsTab;
                break;
            default:
                throw new IllegalArgumentException("navigation item of type " + navigationItem + " is not allowed");
        }

        // for IRC demo we deactivate the arbitratorSettingsTab
        arbitratorSettingsTab.setDisable(true);

        tab.setContent(loaded.view);
        root.getSelectionModel().select(tab);
    }
}
