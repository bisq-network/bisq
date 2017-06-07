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

package io.bitsquare.gui.main.offer;

import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.user.Preferences;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

import javax.inject.Inject;

@FxmlView
public class SellOfferView extends OfferView {
    @FXML
    TabPane root;

    @Override
    protected void initialize() {
        super.initialize();
        AnchorPane.setTopAnchor(root, MainView.scale(0));
        AnchorPane.setRightAnchor(root, MainView.scale(0));
        AnchorPane.setBottomAnchor(root, MainView.scale(0));
        AnchorPane.setLeftAnchor(root, MainView.scale(0));

    }

    @Inject
    public SellOfferView(ViewLoader viewLoader, Navigation navigation, PriceFeedService priceFeedService, Preferences preferences) {
        super(viewLoader, navigation, priceFeedService, preferences);
    }

    @Override
    protected String getCreateOfferTabName() {
        return "Create offer";
    }

    @Override
    protected String getTakeOfferTabName() {
        return "Take offer";
    }
}

