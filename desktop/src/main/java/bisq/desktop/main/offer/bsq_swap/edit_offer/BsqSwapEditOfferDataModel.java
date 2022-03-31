/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation,
either version 3 of the License,
or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful,
but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not,
see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.offer.bsq_swap.edit_offer;

import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferDataModel;

import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.bsq_swap.BsqSwapOfferModel;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.offer.bsq_swap.OpenBsqSwapOfferService;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;
import javax.inject.Named;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class BsqSwapEditOfferDataModel extends BsqSwapOfferDataModel {
    private final OpenBsqSwapOfferService openBsqSwapOfferService;
    protected OpenOffer openOffer;
    protected OpenOffer.State initialState;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    BsqSwapEditOfferDataModel(BsqSwapOfferModel bsqSwapOfferModel,
                              OpenBsqSwapOfferService openBsqSwapOfferService,
                              User user,
                              P2PService p2PService,
                              @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        super(bsqSwapOfferModel,
                user,
                p2PService,
                btcFormatter);

        this.openBsqSwapOfferService = openBsqSwapOfferService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyOpenOffer(OpenOffer openOffer) {
        this.openOffer = openOffer;
        this.initialState = openOffer.getState();

        Offer offer = openOffer.getOffer();
        bsqSwapOfferModel.init(offer.getDirection(), true, offer);
    }

    public void onStartEditOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        openBsqSwapOfferService.editOpenOfferStart(openOffer, resultHandler, errorMessageHandler);
    }

    public void onCancelEditOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (openOffer != null)
            openBsqSwapOfferService.editOpenOfferCancel(openOffer, initialState, resultHandler, errorMessageHandler);
    }

    public void onPublishOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        BsqSwapOfferPayload original = openOffer.getOffer().getBsqSwapOfferPayload().orElseThrow();
        BsqSwapOfferPayload newPayload = BsqSwapOfferPayload.from(original, getPrice().get());

        openBsqSwapOfferService.editOpenOfferPublish(new Offer(newPayload), initialState, () -> {
            openOffer = null;
            resultHandler.handleResult();
        }, errorMessageHandler);
    }
}
