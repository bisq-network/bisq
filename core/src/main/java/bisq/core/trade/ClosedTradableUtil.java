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

package bisq.core.trade;

import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import org.bitcoinj.core.Coin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClosedTradableUtil {
    public static Coin getTotalAmount(List<Tradable> tradableList) {
        return Coin.valueOf(tradableList.stream()
                .flatMap(tradable -> tradable.getOptionalAmountAsLong().stream())
                .mapToLong(value -> value)
                .sum());
    }

    public static Coin getTotalTxFee(List<Tradable> tradableList) {
        return Coin.valueOf(tradableList.stream()
                .mapToLong(tradable -> getTxFee(tradable).getValue())
                .sum());
    }

    public static Map<String, Long> getTotalVolumeByCurrency(List<Tradable> tradableList) {
        Map<String, Long> map = new HashMap<>();
        tradableList.stream()
                .flatMap(tradable -> tradable.getOptionalVolume().stream())
                .forEach(volume -> {
                    String currencyCode = volume.getCurrencyCode();
                    map.putIfAbsent(currencyCode, 0L);
                    map.put(currencyCode, volume.getValue() + map.get(currencyCode));
                });
        return map;
    }

    public static Coin getTxFee(Tradable tradable) {
        return tradable.getOptionalTxFee().orElse(Coin.ZERO);
    }

    public static boolean isOpenOffer(Tradable tradable) {
        return tradable instanceof OpenOffer;
    }

    public static boolean isBsqSwapTrade(Tradable tradable) {
        return tradable instanceof BsqSwapTrade;
    }

    public static boolean isBisqV1Trade(Tradable tradable) {
        return tradable instanceof Trade;
    }

    public static Trade castToTrade(Tradable tradable) {
        return (Trade) tradable;
    }

    public static TradeModel castToTradeModel(Tradable tradable) {
        return (TradeModel) tradable;
    }

    public static BsqSwapTrade castToBsqSwapTrade(Tradable tradable) {
        return (BsqSwapTrade) tradable;
    }
}
