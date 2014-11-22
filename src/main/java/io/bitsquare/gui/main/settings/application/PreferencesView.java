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

import io.bitsquare.gui.CachedView;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This UI is not cached as it is normally only needed once.
 */
public class PreferencesView extends CachedView<PreferencesPM> {

    private static final Logger log = LoggerFactory.getLogger(PreferencesView.class);

    @FXML ComboBox<String> btcDenominationComboBox;
    @FXML CheckBox useAnimationsCheckBox, useEffectsCheckBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private PreferencesView(PreferencesPM model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
    }

    @Override
    public void doActivate() {
        btcDenominationComboBox.setItems(model.getBtcDenominationItems());
        btcDenominationComboBox.getSelectionModel().select(model.btcDenomination().get());

        useAnimationsCheckBox.selectedProperty().bindBidirectional(model.useAnimations());
        useEffectsCheckBox.selectedProperty().bindBidirectional(model.useEffects());

    }

    @Override
    public void doDeactivate() {
        useAnimationsCheckBox.selectedProperty().unbind();
        useEffectsCheckBox.selectedProperty().unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void onSelectBtcDenomination() {
        model.btcDenomination().set(btcDenominationComboBox.getSelectionModel().getSelectedItem());
    }
}
