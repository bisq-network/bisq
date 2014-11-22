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

import io.bitsquare.gui.UIModel;
import io.bitsquare.settings.Preferences;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PreferencesModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(PreferencesModel.class);

    private final Preferences preferences;

    private final ChangeListener<Boolean> useAnimationsListener;
    private final ChangeListener<Boolean> useEffectsListener;
    private final ChangeListener<String> btcDenominationListener;

    final ObservableList<String> btcDenominations;

    final BooleanProperty useAnimations = new SimpleBooleanProperty();
    final BooleanProperty useEffects = new SimpleBooleanProperty();
    final StringProperty btcDenomination = new SimpleStringProperty();


    @Inject
    PreferencesModel(Preferences preferences) {
        this.preferences = preferences;

        btcDenominations = FXCollections.observableArrayList(preferences.getBtcDenominations());
        btcDenominationListener = (ov, oldValue, newValue) -> preferences.setBtcDenomination(newValue);
        useAnimationsListener = (ov, oldValue, newValue) -> preferences.setUseAnimations(newValue);
        useEffectsListener = (ov, oldValue, newValue) -> preferences.setUseEffects(newValue);
    }


    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize() {
        super.initialize();
    }

    @Override
    public void activate() {
        super.activate();

        useAnimations.set(preferences.getUseAnimations());
        useEffects.set(preferences.getUseEffects());
        btcDenomination.set(preferences.getBtcDenomination());

        useAnimations.addListener(useAnimationsListener);
        useEffects.addListener(useEffectsListener);
        btcDenomination.addListener(btcDenominationListener);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        useAnimations.removeListener(useAnimationsListener);
        useEffects.removeListener(useEffectsListener);
        btcDenomination.removeListener(btcDenominationListener);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }

}

