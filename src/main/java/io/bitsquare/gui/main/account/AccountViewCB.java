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
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.NavigationManager;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.main.account.setup.AccountSetupViewCB;
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

public class AccountViewCB extends CachedViewCB<AccountPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountViewCB.class);

    public Tab tab;
    private NavigationManager navigationManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountViewCB(AccountPM presentationModel, NavigationManager navigationManager) {
        super(presentationModel);
        this.navigationManager = navigationManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        if (childController == null) {
            if (presentationModel.getNeedRegistration()) {
                childController = loadView(NavigationItem.ACCOUNT_SETUP);
                tab.setText("Account setup");
            }
            else {
                childController = loadView(NavigationItem.ACCOUNT_SETTINGS);
                tab.setText("Account settings");
            }
        }
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

    @Override
    public Initializable loadView(NavigationItem navigationItem) {
        super.loadView(navigationItem);

        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            Pane view = loader.load();
            tab.setContent(view);
            Initializable childController = loader.getController();
            ((ViewCB) childController).setParent(this);

            if (childController instanceof AccountSetupViewCB)
                ((AccountSetupViewCB) childController).setRemoveCallBack(() -> {
                    removeSetup();
                    return null;
                });

        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + NavigationItem.ACCOUNT_SETUP.getFxmlUrl());
            e.getStackTrace();
        }
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeSetup() {
        childController = null;

        navigationManager.navigationTo(navigationManager.getPreviousMainNavigationItems());
    }

}

