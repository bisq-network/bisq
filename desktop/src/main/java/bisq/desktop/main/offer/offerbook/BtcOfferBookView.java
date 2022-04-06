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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.overlays.windows.BsqSwapOfferDetailsWindow;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.Res;
import bisq.core.offer.OfferDirection;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.layout.GridPane;

@FxmlView
public class BtcOfferBookView extends OfferBookView<GridPane, BtcOfferBookViewModel> {

    @Inject
    BtcOfferBookView(BtcOfferBookViewModel model,
                     Navigation navigation,
                     OfferDetailsWindow offerDetailsWindow,
                     BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow,
                     @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                     PrivateNotificationManager privateNotificationManager,
                     @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys,
                     AccountAgeWitnessService accountAgeWitnessService,
                     SignedWitnessService signedWitnessService) {
        super(model, navigation, offerDetailsWindow, bsqSwapOfferDetailsWindow, formatter, privateNotificationManager, useDevPrivilegeKeys, accountAgeWitnessService, signedWitnessService);
    }

    @Override
    protected String getMarketTitle() {
        return model.getDirection().equals(OfferDirection.BUY) ?
                Res.get("offerbook.availableOffersToBuy", Res.getBaseCurrencyName(), Res.get("shared.fiat")) :
                Res.get("offerbook.availableOffersToSell", Res.getBaseCurrencyName(), Res.get("shared.fiat"));


    }

    @Override
    String getCreateOfferButtonLabel() {
        return Res.getBaseCurrencyName().toUpperCase();
    }
}
