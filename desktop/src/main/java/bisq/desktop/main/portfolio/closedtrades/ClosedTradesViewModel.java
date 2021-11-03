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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.monetary.Volume;
import bisq.core.trade.ClosedTradeUtil;
import bisq.core.trade.model.Tradable;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

import java.util.Map;

public class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private final ClosedTradeUtil closedTradeUtil;
    final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel,
                                 ClosedTradeUtil closedTradeUtil,
                                 AccountAgeWitnessService accountAgeWitnessService) {
        super(dataModel);
        this.closedTradeUtil = closedTradeUtil;
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    public ObservableList<ClosedTradableListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(ClosedTradableListItem item) {
        return item.getTradable().getShortId();
    }

    String getAmount(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getAmountAsString(item.getTradable()) : "";
    }

    String getPrice(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getPriceAsString(item.getTradable()) : "";
    }

    String getPriceDeviation(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getPriceDeviationAsString(item.getTradable()) : "";
    }

    String getVolume(ClosedTradableListItem item, boolean appendCode) {
        return item != null ? closedTradeUtil.getVolumeAsString(item.getTradable(), appendCode) : "";
    }

    String getVolumeCurrency(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getVolumeCurrencyAsString(item.getTradable()) : "";
    }

    String getTxFee(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getTxFeeAsString(item.getTradable()) : "";
    }

    boolean isCurrencyForTradeFeeBtc(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.isCurrencyForTradeFeeBtc(item.getTradable()) : false;
    }

    String getTradeFee(ClosedTradableListItem item, boolean appendCode) {
        return item != null ? closedTradeUtil.getTradeFeeAsString(item.getTradable(), appendCode) : "";
    }

    String getBuyerSecurityDeposit(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getBuyerSecurityDepositAsString(item.getTradable()) : "";
    }

    String getSellerSecurityDeposit(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getSellerSecurityDepositAsString(item.getTradable()) : "";
    }

    String getDirectionLabel(ClosedTradableListItem item) {
        return (item != null) ? DisplayUtils.getDirectionWithCode(dataModel.getDirection(item.getTradable().getOffer()), item.getTradable().getOffer().getCurrencyCode()) : "";
    }

    String getDate(ClosedTradableListItem item) {
        return DisplayUtils.formatDateTime(item.getTradable().getDate());
    }

    String getMarketLabel(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getMarketLabel(item.getTradable()) : "";
    }

    String getState(ClosedTradableListItem item) {
        return item != null ? closedTradeUtil.getStateAsString(item.getTradable()) : "";
    }

    int getNumPastTrades(Tradable tradable) {
        return closedTradeUtil.getNumPastTrades(tradable);
    }

    public Coin getTotalTradeAmount() {
        return dataModel.getTotalAmount();
    }

    public String getTotalAmountWithVolume(Coin totalTradeAmount) {
        return dataModel.getVolumeInUserFiatCurrency(totalTradeAmount)
                .map(volume -> closedTradeUtil.getTotalAmountWithVolumeAsString(totalTradeAmount, volume))
                .orElse("");
    }

    public Map<String, String> getTotalVolumeByCurrency() {
        return closedTradeUtil.getTotalVolumeByCurrencyAsString(dataModel.tradableList.get());
    }

    public String getTotalTxFee(Coin totalTradeAmount) {
        Coin totalTxFee = dataModel.getTotalTxFee();
        return closedTradeUtil.getTotalTxFeeAsString(totalTradeAmount, totalTxFee);
    }

    public String getTotalTradeFeeInBtc(Coin totalTradeAmount) {
        Coin totalTradeFee = dataModel.getTotalTradeFee(true);
        return closedTradeUtil.getTotalTradeFeeInBtcAsString(totalTradeAmount, totalTradeFee);
    }

    public String getTotalTradeFeeInBsq(Coin totalTradeAmount) {
        return dataModel.getVolume(totalTradeAmount, "USD")
                .filter(v -> v.getValue() > 0)
                .map(tradeAmountVolume -> {
                    Coin totalTradeFee = dataModel.getTotalTradeFee(false);
                    Volume bsqVolumeInUsd = dataModel.getBsqVolumeInUsdWithAveragePrice(totalTradeFee); // with 4 decimal
                    return closedTradeUtil.getTotalTradeFeeInBsqAsString(totalTradeFee,
                            tradeAmountVolume,
                            bsqVolumeInUsd);
                })
                .orElse("");
    }
}
