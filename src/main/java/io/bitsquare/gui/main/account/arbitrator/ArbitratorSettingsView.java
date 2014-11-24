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

import io.bitsquare.gui.FxmlView;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.account.arbitrator.registration.ArbitratorRegistrationView;

import javax.inject.Inject;

import viewfx.view.View;
import viewfx.view.ViewLoader;
import viewfx.view.support.CachingViewLoader;
import viewfx.view.support.AbstractView;

import javafx.fxml.FXML;
import javafx.scene.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

// TODO Arbitration is very basic yet
class ArbitratorSettingsView extends AbstractView {

    private ArbitratorRegistrationView arbitratorRegistrationView;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final Stage primaryStage;

    @Inject
    private ArbitratorSettingsView(CachingViewLoader viewLoader, Navigation navigation, Stage primaryStage) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.primaryStage = primaryStage;
    }

    private void loadView(FxmlView navigationItem) {
        View view = viewLoader.load(navigationItem.getLocation());
        arbitratorRegistrationView = (ArbitratorRegistrationView) view;

        final Stage stage = new Stage();
        stage.setTitle("Arbitrator");
        stage.setMinWidth(800);
        stage.setMinHeight(400);
        stage.setWidth(800);
        stage.setHeight(600);
        stage.setX(primaryStage.getX() + 50);
        stage.setY(primaryStage.getY() + 50);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(primaryStage);
        Scene scene = new Scene((Parent) view.getRoot(), 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    public void onArbitratorRegistration() {
        loadView(FxmlView.ARBITRATOR_REGISTRATION);
    }

    @FXML
    public void onArbitratorEdit() {
        loadView(FxmlView.ARBITRATOR_REGISTRATION);
        arbitratorRegistrationView.setEditMode(true);
    }
}

