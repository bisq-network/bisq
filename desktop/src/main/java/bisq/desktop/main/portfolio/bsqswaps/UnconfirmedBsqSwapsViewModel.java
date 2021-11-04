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

package bisq.desktop.main.portfolio.bsqswaps;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.util.DisplayUtils;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.ObservableList;

class UnconfirmedBsqSwapsViewModel extends ActivatableWithDataModel<UnconfirmedBsqSwapsDataModel> implements ViewModel {
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    final AccountAgeWitnessService accountAgeWitnessService;
    private final ClosedTradableManager closedTradableManager;

    @Inject
    public UnconfirmedBsqSwapsViewModel(UnconfirmedBsqSwapsDataModel dataModel,
                                        AccountAgeWitnessService accountAgeWitnessService,
                                        ClosedTradableManager closedTradableManager,
                                        BsqFormatter bsqFormatter,
                                        @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        super(dataModel);
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.closedTradableManager = closedTradableManager;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
    }

    public ObservableList<UnconfirmedBsqSwapsListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(UnconfirmedBsqSwapsListItem item) {
        return item.getBsqSwapTrade().getShortId();
    }

    String getAmount(UnconfirmedBsqSwapsListItem item) {
        if (item == null)
            return "";

        return btcFormatter.formatCoin(item.getBsqSwapTrade().getAmount());
    }

    String getPrice(UnconfirmedBsqSwapsListItem item) {
        if (item == null)
            return "";

        return FormattingUtils.formatPrice(item.getBsqSwapTrade().getPrice());
    }

    String getVolume(UnconfirmedBsqSwapsListItem item) {
        if (item == null)
            return "";

        return VolumeUtil.formatVolumeWithCode(item.getBsqSwapTrade().getVolume());
    }

    String getTxFee(UnconfirmedBsqSwapsListItem item) {
        if (item == null)
            return "";

        return btcFormatter.formatCoinWithCode(Coin.valueOf(item.getBsqSwapTrade().getBsqSwapProtocolModel().getTxFee()));
    }

    String getTradeFee(UnconfirmedBsqSwapsListItem item) {
        if (item == null)
            return "";

        if (wasMyOffer(item.getBsqSwapTrade())) {
            return bsqFormatter.formatCoinWithCode(item.getBsqSwapTrade().getMakerFeeAsLong());
        } else {
            return bsqFormatter.formatCoinWithCode(item.getBsqSwapTrade().getTakerFeeAsLong());
        }
    }

    String getDirectionLabel(UnconfirmedBsqSwapsListItem item) {
        if (item == null)
            return "";

        return DisplayUtils.getDirectionWithCode(dataModel.getDirection(item.getBsqSwapTrade().getOffer()),
                item.getBsqSwapTrade().getOffer().getCurrencyCode());
    }

    String getDate(UnconfirmedBsqSwapsListItem item) {
        return DisplayUtils.formatDateTime(item.getBsqSwapTrade().getDate());
    }

    String getMarketLabel(UnconfirmedBsqSwapsListItem item) {
        if (item == null)
            return "";

        return CurrencyUtil.getCurrencyPair(item.getBsqSwapTrade().getOffer().getCurrencyCode());
    }

    int getConfidence(UnconfirmedBsqSwapsListItem item) {
        if ((item == null))
            return 0;
        return item.getConfirmations();
    }

    int getNumPastTrades(BsqSwapTrade bsqSwapTrade) {
        return closedTradableManager.getNumPastTrades(bsqSwapTrade);
    }

    boolean wasMyOffer(BsqSwapTrade bsqSwapTrade) {
        return dataModel.bsqSwapTradeManager.wasMyOffer(bsqSwapTrade.getOffer());
    }
}
