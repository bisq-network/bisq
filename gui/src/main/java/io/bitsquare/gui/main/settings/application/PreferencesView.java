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

package io.bitsquare.gui.main.settings.application;

import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

@FxmlView
public class PreferencesView extends ActivatableViewAndModel<GridPane, PreferencesViewModel> {

    @FXML ComboBox<String> btcDenominationComboBox;
    @FXML CheckBox useAnimationsCheckBox, useEffectsCheckBox;

    @Inject
    public PreferencesView(PreferencesViewModel model) {
        super(model);
    }

    @Override
    public void doActivate() {
        btcDenominationComboBox.setItems(model.getBtcDenominationItems());
        btcDenominationComboBox.getSelectionModel().select(model.btcDenomination().get());

        // For alpha
        btcDenominationComboBox.setDisable(true);

        useAnimationsCheckBox.selectedProperty().bindBidirectional(model.useAnimations());
        useEffectsCheckBox.selectedProperty().bindBidirectional(model.useEffects());

    }

    @Override
    public void doDeactivate() {
        useAnimationsCheckBox.selectedProperty().unbind();
        useEffectsCheckBox.selectedProperty().unbind();
    }

    @FXML
    void onSelectBtcDenomination() {
        model.btcDenomination().set(btcDenominationComboBox.getSelectionModel().getSelectedItem());
    }
}
