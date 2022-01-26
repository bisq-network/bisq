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

package bisq.desktop.main.portfolio.openoffer;

import bisq.desktop.common.model.ActivatableDataModel;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.TriggerPriceService;
import bisq.core.offer.bsq_swap.OpenBsqSwapOfferService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.FormattingUtils;
import bisq.core.util.PriceUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class OpenOffersDataModel extends ActivatableDataModel {
    private final OpenOfferManager openOfferManager;
    private final OpenBsqSwapOfferService openBsqSwapOfferService;
    private final PriceFeedService priceFeedService;
    private final PriceUtil priceUtil;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;

    private final ObservableList<OpenOfferListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<OpenOffer> tradesListChangeListener;
    private final ChangeListener<Number> currenciesUpdateFlagPropertyListener;

    @Inject
    public OpenOffersDataModel(OpenOfferManager openOfferManager,
                               OpenBsqSwapOfferService openBsqSwapOfferService,
                               PriceFeedService priceFeedService,
                               PriceUtil priceUtil,
                               @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                               BsqFormatter bsqFormatter) {
        this.openOfferManager = openOfferManager;
        this.openBsqSwapOfferService = openBsqSwapOfferService;
        this.priceFeedService = priceFeedService;
        this.priceUtil = priceUtil;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;

        tradesListChangeListener = change -> applyList();
        currenciesUpdateFlagPropertyListener = (observable, oldValue, newValue) -> applyList();
    }

    @Override
    protected void activate() {
        openOfferManager.getObservableList().addListener(tradesListChangeListener);
        priceFeedService.updateCounterProperty().addListener(currenciesUpdateFlagPropertyListener);
        applyList();
    }

    @Override
    protected void deactivate() {
        openOfferManager.getObservableList().removeListener(tradesListChangeListener);
        priceFeedService.updateCounterProperty().removeListener(currenciesUpdateFlagPropertyListener);
    }

    void onActivateOpenOffer(OpenOffer openOffer,
                             ResultHandler resultHandler,
                             ErrorMessageHandler errorMessageHandler) {
        if (openOffer.getOffer().isBsqSwapOffer()) {
            openBsqSwapOfferService.activateOpenOffer(openOffer, resultHandler, errorMessageHandler);
        } else {
            openOfferManager.activateOpenOffer(openOffer, resultHandler, errorMessageHandler);
        }
    }

    void onDeactivateOpenOffer(OpenOffer openOffer,
                               ResultHandler resultHandler,
                               ErrorMessageHandler errorMessageHandler) {
        openOfferManager.deactivateOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    void onRemoveOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        openOfferManager.removeOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }


    public ObservableList<OpenOfferListItem> getList() {
        return list;
    }

    public OfferDirection getDirection(Offer offer) {
        return openOfferManager.isMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(
                openOfferManager.getObservableList().stream()
                        .map(item -> new OpenOfferListItem(item, priceUtil, btcFormatter, bsqFormatter, openOfferManager))
                        .collect(Collectors.toList())
        );

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getOffer().getDate().compareTo(o1.getOffer().getDate()));
    }

    boolean wasTriggered(OpenOffer openOffer) {
        return TriggerPriceService.wasTriggered(priceFeedService.getMarketPrice(openOffer.getOffer().getCurrencyCode()),
                openOffer);
    }
}
