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

    String getTradeId(ClosedTradableListItem item) {
        return item.getTradable().getShortId();
    }

    String getAmount(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getAmountAsString(item.getTradable()) : "";
    }

    String getPrice(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getPriceAsString(item.getTradable()) : "";
    }

    String getPriceDeviation(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getPriceDeviationAsString(item.getTradable()) : "";
    }

    String getVolume(ClosedTradableListItem item, boolean appendCode) {
        return item != null ? closedTradableFormatter.getVolumeAsString(item.getTradable(), appendCode) : "";
    }

    String getVolumeCurrency(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getVolumeCurrencyAsString(item.getTradable()) : "";
    }

    String getTxFee(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getTxFeeAsString(item.getTradable()) : "";
    }

    String getTradeFee(ClosedTradableListItem item, boolean appendCode) {
        return item != null ? closedTradableFormatter.getTradeFeeAsString(item.getTradable(), appendCode) : "";
    }

    String getBuyerSecurityDeposit(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getBuyerSecurityDepositAsString(item.getTradable()) : "";
    }

    String getSellerSecurityDeposit(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getSellerSecurityDepositAsString(item.getTradable()) : "";
    }

    String getDirectionLabel(ClosedTradableListItem item) {
        if ((item != null)) {
            OfferDirection direction = dataModel.getDirection(item.getTradable().getOffer());
            String currencyCode = item.getTradable().getOffer().getCurrencyCode();
            return DisplayUtils.getDirectionWithCode(direction, currencyCode);
        } else {
            return "";
        }
    }

    String getDate(ClosedTradableListItem item) {
        return DisplayUtils.formatDateTime(item.getTradable().getDate());
    }

    String getMarketLabel(ClosedTradableListItem item) {
        return item != null ? CurrencyUtil.getCurrencyPair(item.getTradable().getOffer().getCurrencyCode()) : "";
    }

    String getState(ClosedTradableListItem item) {
        return item != null ? closedTradableFormatter.getStateAsString(item.getTradable()) : "";
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
        return closedTradableFormatter.getTotalVolumeByCurrencyAsString(dataModel.tradableList.get());
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
