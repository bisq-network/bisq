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

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AccountViewCB extends CachedViewCB<AccountPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountViewCB.class);

    Tab tab;
    private Navigation navigation;
    private Navigation.Listener listener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountViewCB(AccountPM presentationModel, Navigation navigation) {
        super(presentationModel);

        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        listener = navigationItems -> {
            if (navigationItems != null &&
                    navigationItems.length == 3 &&
                    navigationItems[1] == Navigation.Item.ACCOUNT)
                loadView(navigationItems[2]);
        };

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        navigation.addListener(listener);

        if (navigation.getCurrentItems().length == 2 &&
                navigation.getCurrentItems()[1] == Navigation.Item.ACCOUNT) {
            if (presentationModel.getNeedRegistration())
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                        Navigation.Item.ACCOUNT_SETUP);
            else
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                        Navigation.Item.ACCOUNT_SETTINGS);
        }
    }

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

        tab.setText((navigationItem == Navigation.Item.ACCOUNT_SETUP) ? "Account setup" : "Account settings");
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            AnchorPane view = loader.load();
            tab.setContent(view);
            Initializable childController = loader.getController();
            ((ViewCB) childController).setParent(this);

        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + Navigation.Item.ACCOUNT_SETUP.getFxmlUrl());
            e.getStackTrace();
        }
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


}

