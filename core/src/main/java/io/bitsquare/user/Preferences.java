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

package io.bitsquare.user;

import io.bitsquare.storage.Storage;

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

public class Preferences implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(Preferences.class);

    // Deactivate mBit for now as most screens are not supporting it yet
    transient private static final List<String> BTC_DENOMINATIONS = Arrays.asList(MonetaryFormat.CODE_BTC/*, MonetaryFormat.CODE_MBTC*/);

    public static List<String> getBtcDenominations() {
        return BTC_DENOMINATIONS;
    }

    transient private final Storage<Preferences> storage;

    // Persisted fields
    private String btcDenomination = MonetaryFormat.CODE_BTC;
    private Boolean useAnimations = true;
    private Boolean useEffects = true;
    private Boolean displaySecurityDepositInfo = true;

    // Observable wrappers
    transient private final StringProperty btcDenominationProperty = new SimpleStringProperty(btcDenomination);
    transient private final BooleanProperty useAnimationsProperty = new SimpleBooleanProperty(useAnimations);
    transient private final BooleanProperty useEffectsProperty = new SimpleBooleanProperty(useEffects);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Preferences(Storage<Preferences> storage) {
        this.storage = storage;

        Preferences persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            setBtcDenomination(persisted.btcDenomination);
            setUseAnimations(persisted.useAnimations);
            setUseEffects(persisted.useEffects);
            displaySecurityDepositInfo = persisted.getDisplaySecurityDepositInfo();
        }

        // Use that to guarantee update of the serializable field and to make a storage update in case of a change
        btcDenominationProperty.addListener((ov) -> {
            btcDenomination = btcDenominationProperty.get();
            storage.queueUpForSave();
        });
        useAnimationsProperty.addListener((ov) -> {
            useAnimations = useAnimationsProperty.get();
            storage.queueUpForSave();
        });
        useEffectsProperty.addListener((ov) -> {
            useEffects = useEffectsProperty.get();
            storage.queueUpForSave();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setBtcDenomination(String btcDenominationProperty) {
        this.btcDenominationProperty.set(btcDenominationProperty);
    }

    public void setUseAnimations(boolean useAnimationsProperty) {
        this.useAnimationsProperty.set(useAnimationsProperty);
    }

    public void setUseEffects(boolean useEffectsProperty) {
        this.useEffectsProperty.set(useEffectsProperty);
    }

    public void setDisplaySecurityDepositInfo(Boolean displaySecurityDepositInfo) {
        this.displaySecurityDepositInfo = displaySecurityDepositInfo;
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getBtcDenomination() {
        return btcDenominationProperty.get();
    }

    public boolean getUseEffects() {
        return useEffectsProperty.get();
    }

    public boolean getUseAnimations() {
        return useAnimationsProperty.get();
    }

    public Boolean getDisplaySecurityDepositInfo() {
        return displaySecurityDepositInfo;
    }

    public StringProperty btcDenominationProperty() {
        return btcDenominationProperty;
    }

    public BooleanProperty useAnimationsProperty() {
        return useAnimationsProperty;
    }

    public BooleanProperty useEffectsPropertyProperty() {
        return useEffectsProperty;
    }


}
