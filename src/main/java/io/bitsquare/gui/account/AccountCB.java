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

package io.bitsquare.gui.account;

import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.CodeBehind;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.account.setup.SetupPM;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountCB extends CachedCodeBehind<SetupPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountCB.class);
    public Button registrationButton;
    public Label headLineLabel;
    public Label titleLabel;
    public Tab setupTab;
    private Pane setupView;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AccountCB(SetupPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();
    }

    public void onRegister(ActionEvent actionEvent) {
        loadViewAndGetChildController(NavigationItem.SETUP);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public CodeBehind<? extends PresentationModel> loadViewAndGetChildController(NavigationItem navigationItem) {
        super.loadViewAndGetChildController(navigationItem);

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            setupView = loader.load();
            setupTab.setContent(setupView);
            childController = loader.getController();

            ((CodeBehind) childController).setParentController(this);

        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + NavigationItem.ACCOUNT.getFxmlUrl());
            log.error(e.getCause().toString());
            log.error(e.getMessage());
            log.error(e.getStackTrace().toString());
        }

        return null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}

