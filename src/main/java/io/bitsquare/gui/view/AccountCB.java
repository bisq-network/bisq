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

package io.bitsquare.gui.view;

import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.CodeBehind;
import io.bitsquare.gui.MainController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.pm.AccountPM;
import io.bitsquare.gui.view.account.AccountSetupCB;
import io.bitsquare.util.BSFXMLLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountCB extends CachedCodeBehind<AccountPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountCB.class);

    public Tab tab;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountCB(AccountPM presentationModel) {
        super(presentationModel);
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

        final BSFXMLLoader loader = new BSFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            Pane view = loader.load();
            tab.setContent(view);
            Initializable childController = loader.getController();
            ((CodeBehind) childController).setParentController(this);

            if (childController instanceof AccountSetupCB)
                ((AccountSetupCB) childController).setRemoveCallBack(() -> {
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

        NavigationItem previousItem = MainController.GET_INSTANCE().getPreviousNavigationItem();
        if (previousItem == null)
            previousItem = NavigationItem.HOME;

        MainController.GET_INSTANCE().loadViewAndGetChildController(previousItem);
    }

}

