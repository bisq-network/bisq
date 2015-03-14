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

import io.bitsquare.gui.main.account.arbitrator.registration.ArbitratorRegistrationView;
import io.bitsquare.viewfx.view.AbstractView;
import io.bitsquare.viewfx.view.CachingViewLoader;
import io.bitsquare.viewfx.view.FxmlView;
import io.bitsquare.viewfx.view.View;
import io.bitsquare.viewfx.view.ViewLoader;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

@FxmlView
public class ArbitratorSettingsView extends AbstractView {

    private final ViewLoader viewLoader;
    private final Stage primaryStage;

    @Inject
    private ArbitratorSettingsView(CachingViewLoader viewLoader, Stage primaryStage) {
        this.viewLoader = viewLoader;
        this.primaryStage = primaryStage;
    }

    @FXML
    public void onArbitratorRegistration() {
        View view = viewLoader.load(ArbitratorRegistrationView.class);
        showStage(view);
    }

    @FXML
    public void onArbitratorEdit() {
        View view = viewLoader.load(ArbitratorRegistrationView.class);
        showStage(view);
        ((ArbitratorRegistrationView) view).setEditMode(true);
    }

    private void showStage(View view) {
        Stage stage = new Stage();
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
}

