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

import io.bitsquare.common.model.ActivatableWithDataModel;
import io.bitsquare.common.model.ViewModel;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

class PreferencesViewModel extends ActivatableWithDataModel<PreferencesDataModel> implements ViewModel {

    @Inject
    public PreferencesViewModel(PreferencesDataModel dataModel) {
        super(dataModel);
    }

    public ObservableList<String> getBtcDenominationItems() {
        return dataModel.btcDenominations;
    }

    BooleanProperty useAnimations() {
        return dataModel.useAnimations;
    }

    BooleanProperty useEffects() {
        return dataModel.useEffects;
    }

    StringProperty btcDenomination() {
        return dataModel.btcDenomination;
    }


}
