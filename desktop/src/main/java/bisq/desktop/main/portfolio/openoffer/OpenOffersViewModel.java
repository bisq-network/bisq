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

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.PriceUtil;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.ObservableList;

import static com.google.common.base.Preconditions.checkNotNull;

class OpenOffersViewModel extends ActivatableWithDataModel<OpenOffersDataModel> implements ViewModel {
    private final P2PService p2PService;
    private final PriceUtil priceUtil;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;


    @Inject
    public OpenOffersViewModel(OpenOffersDataModel dataModel,
                               P2PService p2PService,
                               PriceUtil priceUtil,
                               @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                               BsqFormatter bsqFormatter) {
        super(dataModel);

        this.p2PService = p2PService;
        this.priceUtil = priceUtil;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    protected void activate() {
        priceUtil.recalculateBsq30DayAveragePrice();
    }

    void onActivateOpenOffer(OpenOffer openOffer,
                             ResultHandler resultHandler,
                             ErrorMessageHandler errorMessageHandler) {
        dataModel.onActivateOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    void onDeactivateOpenOffer(OpenOffer openOffer,
                               ResultHandler resultHandler,
                               ErrorMessageHandler errorMessageHandler) {
        dataModel.onDeactivateOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    void onRemoveOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        dataModel.onRemoveOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }

    public ObservableList<OpenOfferListItem> getList() {
        return dataModel.getList();
    }

    String getOfferId(OpenOfferListItem item) {
        return item.getOffer().getShortId();
    }

    String getAmount(OpenOfferListItem item) {
        return (item != null) ? DisplayUtils.formatAmount(item.getOffer(), btcFormatter) : "";
    }

    String getPrice(OpenOfferListItem item) {
        if ((item == null))
            return "";

        Offer offer = item.getOffer();
        Price price = offer.getPrice();
        if (price != null) {
            return FormattingUtils.formatPrice(price);
        } else {
            return Res.get("shared.na");
        }
    }

    String getPriceDeviation(OpenOfferListItem item) {
        Offer offer = item.getOffer();
        return priceUtil.getMarketBasedPrice(offer, offer.getMirroredDirection())
                .map(FormattingUtils::formatPercentagePrice)
                .orElse("");
    }

    Double getPriceDeviationAsDouble(OpenOfferListItem item) {
        Offer offer = item.getOffer();
        return priceUtil.getMarketBasedPrice(offer, offer.getMirroredDirection()).orElse(0d);
    }

    String getVolume(OpenOfferListItem item) {
        return (item != null) ? DisplayUtils.formatVolume(item.getOffer(), false, 0) + " " + item.getOffer().getCurrencyCode() : "";
    }

    String getDirectionLabel(OpenOfferListItem item) {
        if ((item == null))
            return "";

        return DisplayUtils.getDirectionWithCode(dataModel.getDirection(item.getOffer()), item.getOffer().getCurrencyCode());
    }

    String getMarketLabel(OpenOfferListItem item) {
        if ((item == null))
            return "";

        return CurrencyUtil.getCurrencyPair(item.getOffer().getCurrencyCode());
    }

    String getPaymentMethod(OpenOfferListItem item) {
        String result = "";
        if (item != null) {
            Offer offer = item.getOffer();
            checkNotNull(offer);
            checkNotNull(offer.getPaymentMethod());
            result = offer.getPaymentMethodNameWithCountryCode();
        }
        return result;
    }

    String getDate(OpenOfferListItem item) {
        return DisplayUtils.formatDateTime(item.getOffer().getDate());
    }

    boolean isDeactivated(OpenOfferListItem item) {
        return item != null && item.getOpenOffer() != null && item.getOpenOffer().isDeactivated();
    }

    boolean isBootstrappedOrShowPopup() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }

    public String getMakerFeeAsString(OpenOffer openOffer) {
        Offer offer = openOffer.getOffer();
        return offer.isCurrencyForMakerFeeBtc() ?
                btcFormatter.formatCoinWithCode(offer.getMakerFee()) :
                bsqFormatter.formatCoinWithCode(offer.getMakerFee());
    }

    String getTriggerPrice(OpenOfferListItem item) {
        if ((item == null)) {
            return "";
        }

        Offer offer = item.getOffer();
        long triggerPrice = item.getOpenOffer().getTriggerPrice();
        if (!offer.isUseMarketBasedPrice() || triggerPrice <= 0) {
            return Res.get("shared.na");
        } else {
            return PriceUtil.formatMarketPrice(triggerPrice, offer.getCurrencyCode());
        }
    }
}
