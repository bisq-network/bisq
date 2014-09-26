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

package io.bitsquare.gui.main.account.arbitrator;

import io.bitsquare.BitSquare;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.account.arbitrator.registration.ArbitratorRegistrationViewCB;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Arbitration is very basic yet
public class ArbitratorSettingsViewCB extends CachedViewCB {

    private static final Logger log = LoggerFactory.getLogger(ArbitratorSettingsViewCB.class);

    private Navigation navigation;

    private ArbitratorRegistrationViewCB arbitratorRegistrationViewCB;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ArbitratorSettingsViewCB(Navigation navigation) {
        super();
        this.navigation = navigation;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
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
        // don't use caching here, cause exc. -> need to investigate and is rarely called so no caching is better
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
        try {
            final Parent view = loader.load();
            arbitratorRegistrationViewCB = loader.getController();

            final Stage rootStage = BitSquare.getPrimaryStage();
            final Stage stage = new Stage();
            stage.setTitle("Arbitrator");
            stage.setMinWidth(800);
            stage.setMinHeight(400);
            stage.setWidth(800);
            stage.setHeight(600);
            stage.setX(rootStage.getX() + 50);
            stage.setY(rootStage.getY() + 50);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(rootStage);
            Scene scene = new Scene(view, 800, 600);
            stage.setScene(scene);
            stage.show();

            return arbitratorRegistrationViewCB;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onArbitratorRegistration() {
        loadView(Navigation.Item.ARBITRATOR_REGISTRATION);
    }

    @FXML
    public void onArbitratorEdit() {
        loadView(Navigation.Item.ARBITRATOR_REGISTRATION);
        arbitratorRegistrationViewCB.setEditMode(true);
    }
}

