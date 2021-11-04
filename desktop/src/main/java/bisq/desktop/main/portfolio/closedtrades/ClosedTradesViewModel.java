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

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Volume;
import bisq.core.offer.OfferDirection;
import bisq.core.trade.ClosedTradableFormatter;
import bisq.core.trade.model.Tradable;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import java.util.Map;

public class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private final ClosedTradableFormatter closedTradableFormatter;

    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel, ClosedTradableFormatter closedTradableFormatter) {
        super(dataModel);

        this.closedTradableFormatter = closedTradableFormatter;
    }

    String getTradeId(Tradable item) {
        return item.getShortId();
    }

    String getAmount(Tradable item) {
        return item != null ? closedTradableFormatter.getAmountAsString(item) : "";
    }

    String getPrice(Tradable item) {
        return item != null ? closedTradableFormatter.getPriceAsString(item) : "";
    }

    String getPriceDeviation(Tradable item) {
        return item != null ? closedTradableFormatter.getPriceDeviationAsString(item) : "";
    }

    String getVolume(Tradable item, boolean appendCode) {
        return item != null ? closedTradableFormatter.getVolumeAsString(item, appendCode) : "";
    }

    String getVolumeCurrency(Tradable item) {
        return item != null ? closedTradableFormatter.getVolumeCurrencyAsString(item) : "";
    }

    String getTxFee(Tradable item) {
        return item != null ? closedTradableFormatter.getTxFeeAsString(item) : "";
    }

    String getTradeFee(Tradable item, boolean appendCode) {
        return item != null ? closedTradableFormatter.getTradeFeeAsString(item, appendCode) : "";
    }

    String getBuyerSecurityDeposit(Tradable item) {
        return item != null ? closedTradableFormatter.getBuyerSecurityDepositAsString(item) : "";
    }

    String getSellerSecurityDeposit(Tradable item) {
        return item != null ? closedTradableFormatter.getSellerSecurityDepositAsString(item) : "";
    }

    String getDirectionLabel(Tradable item) {
        if ((item != null)) {
            OfferDirection direction = dataModel.getDirection(item.getOffer());
            String currencyCode = item.getOffer().getCurrencyCode();
            return DisplayUtils.getDirectionWithCode(direction, currencyCode);
        } else {
            return "";
        }
    }

    String getDate(Tradable item) {
        return DisplayUtils.formatDateTime(item.getDate());
    }

    String getMarketLabel(Tradable item) {
        return item != null ? CurrencyUtil.getCurrencyPair(item.getOffer().getCurrencyCode()) : "";
    }

    String getState(Tradable item) {
        return item != null ? closedTradableFormatter.getStateAsString(item) : "";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Used in ClosedTradesSummaryWindow
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getTotalTradeAmount() {
        return dataModel.getTotalAmount();
    }

    public String getTotalAmountWithVolume(Coin totalTradeAmount) {
        return dataModel.getVolumeInUserFiatCurrency(totalTradeAmount)
                .map(volume -> closedTradableFormatter.getTotalAmountWithVolumeAsString(totalTradeAmount, volume))
                .orElse("");
    }

    public Map<String, String> getTotalVolumeByCurrency() {
        return closedTradableFormatter.getTotalVolumeByCurrencyAsString(dataModel.getList());
    }

    public String getTotalTxFee(Coin totalTradeAmount) {
        Coin totalTxFee = dataModel.getTotalTxFee();
        return closedTradableFormatter.getTotalTxFeeAsString(totalTradeAmount, totalTxFee);
    }

    public String getTotalTradeFeeInBtc(Coin totalTradeAmount) {
        Coin totalTradeFee = dataModel.getTotalTradeFee(true);
        return closedTradableFormatter.getTotalTradeFeeInBtcAsString(totalTradeAmount, totalTradeFee);
    }

    public String getTotalTradeFeeInBsq(Coin totalTradeAmount) {
        return dataModel.getVolume(totalTradeAmount, "USD")
                .filter(v -> v.getValue() > 0)
                .map(tradeAmountVolume -> {
                    Coin totalTradeFee = dataModel.getTotalTradeFee(false);
                    Volume bsqVolumeInUsd = dataModel.getBsqVolumeInUsdWithAveragePrice(totalTradeFee); // with 4 decimal
                    return closedTradableFormatter.getTotalTradeFeeInBsqAsString(totalTradeFee,
                            tradeAmountVolume,
                            bsqVolumeInUsd);
                })
                .orElse("");
    }
}
