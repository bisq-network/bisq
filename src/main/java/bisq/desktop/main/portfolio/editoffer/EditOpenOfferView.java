/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.editoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.offer.EditableOfferView;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.Transitions;

import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.user.Preferences;

import com.google.inject.Inject;

@FxmlView
public class EditOpenOfferView extends EditableOfferView<EditOpenOfferViewModel> {

    @Inject
    private EditOpenOfferView(EditOpenOfferViewModel model, Navigation navigation, Preferences preferences, Transitions transitions, OfferDetailsWindow offerDetailsWindow, BSFormatter btcFormatter, BsqFormatter bsqFormatter) {
        super(model, navigation, preferences, transitions, offerDetailsWindow, btcFormatter, bsqFormatter);
    }

    @Override
    protected void doActivate() {
        super.doActivate();

        hidePaymentGroup();
        hideOptionsGroup();
    }

    public void initWithData(OpenOffer openOffer) {
        super.initWithData(openOffer.getOffer().getDirection(), CurrencyUtil.getTradeCurrency(openOffer.getOffer().getCurrencyCode()).get());

        model.initWithData(openOffer);
    }
}
