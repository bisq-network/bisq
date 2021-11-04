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

package bisq.desktop.main.offer.bsq_swap;

import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.TradeCurrency;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bsq_swap.BsqSwapOfferModel;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import javax.inject.Named;

import lombok.Getter;
import lombok.experimental.Delegate;

public abstract class BsqSwapOfferDataModel extends ActivatableDataModel {
    protected final User user;
    private final P2PService p2PService;
    private final CoinFormatter btcFormatter;

    // We use the BsqSwapOfferModel from core as delegate
    // This contains all non UI specific domain aspects and is re-used from the API.
    @Delegate(excludes = ExcludesDelegateMethods.class)
    protected final BsqSwapOfferModel bsqSwapOfferModel;

    @Getter
    protected TradeCurrency tradeCurrency;
    @Getter
    private boolean isTabSelected;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqSwapOfferDataModel(BsqSwapOfferModel bsqSwapOfferModel,
                                 User user,
                                 P2PService p2PService,
                                 @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        this.bsqSwapOfferModel = bsqSwapOfferModel;
        this.user = user;
        this.p2PService = p2PService;
        this.btcFormatter = btcFormatter;
    }

    @Override
    protected void activate() {
        bsqSwapOfferModel.doActivate();
    }

    @Override
    protected void deactivate() {
        bsqSwapOfferModel.doDeactivate();
    }

    public void onTabSelected(boolean isSelected) {
        this.isTabSelected = isSelected;
    }

    protected void createListeners() {
        bsqSwapOfferModel.createListeners();
    }

    protected void addListeners() {
        bsqSwapOfferModel.addListeners();
    }

    protected void removeListeners() {
        bsqSwapOfferModel.removeListeners();
    }

    public boolean canPlaceOrTakeOffer() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }

    public void calculateAmount() {
        bsqSwapOfferModel.calculateAmount(amount -> DisplayUtils.reduceTo4Decimals(amount, btcFormatter));
    }

    private interface ExcludesDelegateMethods {
        void init(OfferDirection direction, boolean isMaker);

        void createListeners();

        void addListeners();

        void removeListeners();
    }
}
