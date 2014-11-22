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

import io.bitsquare.gui.CachedView;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.View;
import io.bitsquare.gui.ViewLoader;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountView extends CachedView {

    private static final Logger log = LoggerFactory.getLogger(AccountView.class);

    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    @FXML Tab accountSettingsTab, arbitratorSettingsTab;

    private final AccountPM model;
    private final ViewLoader viewLoader;
    private final Navigation navigation;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountView(AccountPM model, ViewLoader viewLoader, Navigation navigation) {
        this.model = model;
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
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

        super.initialize(url, rb);
    }

    @Override
    public void doActivate() {
        navigation.addListener(navigationListener);
        ((TabPane) root).getSelectionModel().selectedItemProperty().addListener(tabChangeListener);

        if (navigation.getCurrentItems().length == 2 &&
                navigation.getCurrentItems()[1] == Navigation.Item.ACCOUNT) {
            if (model.getNeedRegistration()) {
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                        Navigation.Item.ACCOUNT_SETUP);
            }
            else {
                if (((TabPane) root).getSelectionModel().getSelectedItem() == accountSettingsTab)
                    navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                            Navigation.Item.ACCOUNT_SETTINGS);
                else
                    navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                            Navigation.Item.ARBITRATOR_SETTINGS);
            }
        }
    }

    @Override
    public void doDeactivate() {
        navigation.removeListener(navigationListener);
        ((TabPane) root).getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
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
        ((TabPane) root).getSelectionModel().select(tab);
        Initializable childController = loaded.controller;
        ((View) childController).setParent(this);

        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


}

