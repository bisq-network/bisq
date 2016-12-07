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

package io.bitsquare.gui.main.dao.tokenwallet;

import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.util.BSFormatter;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;

@FxmlView
public class TokenWalletView extends ActivatableViewAndModel<GridPane, Activatable> {


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TokenWalletView(BSFormatter formatter) {
        super();
    }

    @Override
    public void initialize() {
    }


    @Override
    protected void activate() {
    }

    @Override
    protected void deactivate() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialize
    ///////////////////////////////////////////////////////////////////////////////////////////

   
}
