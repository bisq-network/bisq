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
    private static final long serialVersionUID = 1L;
    transient private static final Logger log = LoggerFactory.getLogger(Preferences.class);

    // Deactivate mBit for now as most screens are not supporting it yet
    transient private static final List<String> BTC_DENOMINATIONS = Arrays.asList(MonetaryFormat.CODE_BTC/*, MonetaryFormat.CODE_MBTC*/);

    public static List<String> getBtcDenominations() {
        return BTC_DENOMINATIONS;
    }

    transient private final Storage<Preferences> storage;

    // Persisted fields
    private String _btcDenomination = MonetaryFormat.CODE_BTC;
    private Boolean _useAnimations = true;
    private Boolean _useEffects = true;
    private Boolean displaySecurityDepositInfo = true;

    // Observable wrappers
    transient private final StringProperty btcDenomination = new SimpleStringProperty(_btcDenomination);
    transient private final BooleanProperty useAnimations = new SimpleBooleanProperty(_useAnimations);
    transient private final BooleanProperty useEffects = new SimpleBooleanProperty(_useEffects);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Preferences(Storage<Preferences> storage) {
        this.storage = storage;

        Preferences persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            setBtcDenomination(persisted._btcDenomination);
            setUseAnimations(persisted._useAnimations);
            setUseEffects(persisted._useEffects);
            displaySecurityDepositInfo = persisted.getDisplaySecurityDepositInfo();
        }

        // Use that to guarantee update of the serializable field and to make a storage update in case of a change
        btcDenomination.addListener((ov) -> {
            _btcDenomination = btcDenomination.get();
            storage.save();
        });
        useAnimations.addListener((ov) -> {
            _useAnimations = useAnimations.get();
            storage.save();
        });
        useEffects.addListener((ov) -> {
            _useEffects = useEffects.get();
            storage.save();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setBtcDenomination(String btcDenomination) {
        this.btcDenomination.set(btcDenomination);
    }

    public void setUseAnimations(boolean useAnimations) {
        this.useAnimations.set(useAnimations);
    }

    public void setUseEffects(boolean useEffects) {
        this.useEffects.set(useEffects);
    }

    public void setDisplaySecurityDepositInfo(Boolean displaySecurityDepositInfo) {
        this.displaySecurityDepositInfo = displaySecurityDepositInfo;
        storage.save();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getBtcDenomination() {
        return btcDenomination.get();
    }

    public boolean getUseEffects() {
        return useEffects.get();
    }

    public boolean getUseAnimations() {
        return useAnimations.get();
    }

    public Boolean getDisplaySecurityDepositInfo() {
        return displaySecurityDepositInfo;
    }

    public StringProperty btcDenominationProperty() {
        return btcDenomination;
    }

    public BooleanProperty useAnimationsProperty() {
        return useAnimations;
    }

    public BooleanProperty useEffectsProperty() {
        return useEffects;
    }


}
