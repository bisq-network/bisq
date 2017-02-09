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

import io.bitsquare.messages.provider.price.PriceFeedService;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.messages.user.Preferences;

import javax.inject.Inject;

@FxmlView
public class SellOfferView extends OfferView {

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

