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

package bisq.desktop.main.portfolio.atomictrades;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.util.DisplayUtils;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.trade.atomic.BsqSwapTrade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class AtomicTradesViewModel extends ActivatableWithDataModel<AtomicTradeDataModel> implements ViewModel {
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public AtomicTradesViewModel(AtomicTradeDataModel dataModel,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 BsqFormatter bsqFormatter,
                                 @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        super(dataModel);
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
    }

    public ObservableList<AtomicTradeListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(AtomicTradeListItem item) {
        return item.getAtomicTrade().getShortId();
    }

    String getAmount(AtomicTradeListItem item) {
        if (item == null)
            return "";

        return btcFormatter.formatCoin(item.getAtomicTrade().getAmount());
    }

    String getPrice(AtomicTradeListItem item) {
        if (item == null)
            return "";

        return FormattingUtils.formatPrice(item.getAtomicTrade().getPrice());
    }

    String getVolume(AtomicTradeListItem item) {
        if (item == null)
            return "";

        return DisplayUtils.formatVolumeWithCode(item.getAtomicTrade().getTradeVolume());
    }

    String getTxFee(AtomicTradeListItem item) {
        if (item == null)
            return "";

        return btcFormatter.formatCoin(Coin.valueOf(item.getAtomicTrade().getMiningFeePerByte()));
    }

    String getTradeFee(AtomicTradeListItem item) {
        if (item == null)
            return "";

        if (wasMyOffer(item.getAtomicTrade())) {
            return bsqFormatter.formatCoinWithCode(item.getAtomicTrade().getMakerFee());
        } else {
            return bsqFormatter.formatCoinWithCode(item.getAtomicTrade().getTakerFee());
        }
    }

    String getDirectionLabel(AtomicTradeListItem item) {
        if (item == null)
            return "";

        return DisplayUtils.getDirectionWithCode(dataModel.getDirection(item.getAtomicTrade().getOffer()),
                item.getAtomicTrade().getOffer().getCurrencyCode());
    }

    String getDate(AtomicTradeListItem item) {
        return DisplayUtils.formatDateTime(item.getAtomicTrade().getDate());
    }

    String getMarketLabel(AtomicTradeListItem item) {
        if ((item == null))
            return "";

        return CurrencyUtil.getCurrencyPair(item.getAtomicTrade().getOffer().getCurrencyCode());
    }

    String getState(AtomicTradeListItem item) {
        if ((item == null))
            return "";

        if (item.getAtomicTrade().isCompleted()) {
            return Res.get("portfolio.atomic.completed");
        }
        return Res.get("portfolio.atomic.waiting");
    }

    int getNumPastTrades(BsqSwapTrade bsqSwapTrade) {
        // TODO(sq): include closed trades in count
        var atomic = dataModel.bsqSwapTradeManager.getObservableList().stream();
        return atomic
                .filter(e -> {
                    var candidate = e.getPeerNodeAddress();
                    var current = bsqSwapTrade.getPeerNodeAddress();
                    return candidate != null &&
                            current != null &&
                            candidate.getFullAddress().equals(current.getFullAddress());
                })
                .collect(Collectors.toSet())
                .size();
    }

    boolean wasMyOffer(BsqSwapTrade bsqSwapTrade) {
        return dataModel.bsqSwapTradeManager.wasMyOffer(bsqSwapTrade.getOffer());
    }
}
