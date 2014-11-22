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

package io.bitsquare.gui.main.account.content.seedwords;

import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;

import com.google.inject.Inject;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

class SeedWordsPM extends PresentationModel<SeedWordsModel> {

    final StringProperty seedWords = new SimpleStringProperty();
    private final BSFormatter formatter;


    @Inject
    public SeedWordsPM(SeedWordsModel model, BSFormatter formatter) {
        super(model);
        this.formatter = formatter;
    }


    @Override
    public void initialize() {
        super.initialize();

        if (model.getMnemonicCode() != null)
            seedWords.set(formatter.mnemonicCodeToString(model.getMnemonicCode()));
    }

}
