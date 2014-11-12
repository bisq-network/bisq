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

package io.bitsquare.preferences;

import io.bitsquare.persistence.Persistence;

import org.bitcoinj.utils.MonetaryFormat;

import java.io.Serializable;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationPreferences implements Serializable {
    private static final long serialVersionUID = 7995048077355006861L;
    private static final Logger log = LoggerFactory.getLogger(ApplicationPreferences.class);

    private List<String> btcDenominations = Arrays.asList(MonetaryFormat.CODE_BTC, MonetaryFormat.CODE_MBTC);


    // Needed for persistence as Property objects are transient (not serializable)
    // Will be probably removed when we have another persistence solution in place
    private String btcDenominationString = MonetaryFormat.CODE_BTC;
    private Boolean useAnimationsBoolean = true;
    private Boolean useEffectsBoolean = true;

    final transient StringProperty btcDenomination = new SimpleStringProperty(btcDenominationString);
    final transient BooleanProperty useAnimations = new SimpleBooleanProperty(useAnimationsBoolean);
    final transient BooleanProperty useEffects = new SimpleBooleanProperty(useEffectsBoolean);
    private Persistence persistence;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ApplicationPreferences(Persistence persistence) {
        this.persistence = persistence;

        applyPersistedSettings();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyPersistedSettings() {
        Object data = persistence.read(this, "btcDenomination");
        if (data instanceof String) {
            btcDenominationString = (String) data;
            this.btcDenomination.set(btcDenominationString);
        }

        data = persistence.read(this, "useEffects");
        if (data instanceof Boolean) {
            useEffectsBoolean = (Boolean) data;
            this.useEffects.set(useEffectsBoolean);
        }

        data = persistence.read(this, "useAnimations");
        if (data instanceof Boolean) {
            useAnimationsBoolean = (Boolean) data;
            this.useAnimations.set(useAnimationsBoolean);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters/Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<String> getBtcDenominations() {
        return btcDenominations;
    }

    // btcDenomination
    public String getBtcDenomination() {
        return btcDenomination.get();
    }

    public StringProperty btcDenominationProperty() {
        return btcDenomination;
    }

    public void setBtcDenomination(String btcDenomination) {
        persistence.write(this, "btcDenomination", btcDenomination);
        btcDenominationString = btcDenomination;
        this.btcDenomination.set(btcDenomination);
    }

    // for persistence
    public String getBtcDenominationString() {
        return btcDenominationString;
    }


    // useAnimations
    public boolean getUseAnimations() {
        return useAnimations.get();
    }

    public BooleanProperty useAnimationsProperty() {
        return useAnimations;
    }

    public void setUseAnimations(boolean useAnimations) {
        persistence.write(this, "useAnimations", useAnimations);
        useAnimationsBoolean = useAnimations;
        this.useAnimations.set(useAnimations);
    }

    // for persistence
    public boolean getUseAnimationsBooleanBoolean() {
        return useAnimationsBoolean;
    }

    // useEffects
    public boolean getUseEffects() {
        return useEffects.get();
    }

    public BooleanProperty useEffectsProperty() {
        return useEffects;
    }

    public void setUseEffects(boolean useEffects) {
        persistence.write(this, "useEffects", useEffects);
        useEffectsBoolean = useEffects;
        this.useEffects.set(useEffects);
    }

    // for persistence
    public boolean getUseEffectsBoolean() {
        return useEffectsBoolean;
    }

}
