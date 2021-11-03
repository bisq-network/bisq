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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.MakerTrade;
import bisq.core.trade.model.TakerTrade;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.AveragePriceUtil.getAveragePriceTuple;
import static bisq.core.util.FormattingUtils.formatPercentagePrice;
import static bisq.core.util.VolumeUtil.formatVolume;
import static bisq.core.util.VolumeUtil.formatVolumeWithCode;

@Slf4j
@Singleton
public class ClosedTradeUtil {
    private final ClosedTradableManager closedTradableManager;
    private final BsqSwapTradeManager bsqSwapTradeManager;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final KeyRing keyRing;
    private final TradeStatisticsManager tradeStatisticsManager;

    @Inject
    public ClosedTradeUtil(ClosedTradableManager closedTradableManager,
                           BsqSwapTradeManager bsqSwapTradeManager,
                           BsqWalletService bsqWalletService,
                           Preferences preferences,
                           KeyRing keyRing,
                           TradeStatisticsManager tradeStatisticsManager) {
        this.closedTradableManager = closedTradableManager;
        this.bsqSwapTradeManager = bsqSwapTradeManager;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.keyRing = keyRing;
        this.tradeStatisticsManager = tradeStatisticsManager;
    }

    public static Coin getTotalAmount(List<Tradable> tradableList) {
        return Coin.valueOf(tradableList.stream()
                .flatMap(tradable -> tradable.getOptionalAmountAsLong().stream())
                .mapToLong(value -> value)
                .sum());
    }

    public String getPriceAsString(Tradable tradable) {
        return tradable.getOptionalPrice().map(FormattingUtils::formatPrice).orElse("");
    }

    public String getPriceDeviationAsString(Tradable tradable) {
        if (tradable.getOffer().isUseMarketBasedPrice()) {
            return formatPercentagePrice(tradable.getOffer().getMarketPriceMargin());
        } else {
            return Res.get("shared.na");
        }
    }

    public String getVolumeAsString(Tradable tradable, boolean appendCode) {
        return tradable.getOptionalVolume().map(volume -> formatVolume(volume, appendCode)).orElse("");
    }

    public String getVolumeCurrencyAsString(Tradable tradable) {
        return tradable.getOptionalVolume().map(Volume::getCurrencyCode).orElse("");
    }

    public Map<String, Long> getTotalVolumeByCurrency(List<Tradable> tradableList) {
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

    public Map<String, String> getTotalVolumeByCurrencyAsString(List<Tradable> tradableList) {
        return getTotalVolumeByCurrency(tradableList).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            String currencyCode = entry.getKey();
                            Monetary monetary;
                            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                                monetary = Altcoin.valueOf(currencyCode, entry.getValue());
                            } else {
                                monetary = Fiat.valueOf(currencyCode, entry.getValue());
                            }
                            return formatVolumeWithCode(new Volume(monetary));
                        }
                ));
    }

    public Volume getBsqVolumeInUsdWithAveragePrice(Coin amount) {
        Tuple2<Price, Price> tuple = getAveragePriceTuple(preferences, tradeStatisticsManager, 30);
        Price usdPrice = tuple.first;
        long value = Math.round(amount.value * usdPrice.getValue() / 100d);
        return new Volume(Fiat.valueOf("USD", value));
    }

    public Coin getTotalTxFee(List<Tradable> tradableList) {
        return Coin.valueOf(tradableList.stream()
                .mapToLong(tradable -> getTxFee(tradable).getValue())
                .sum());
    }

    public boolean isCurrencyForTradeFeeBtc(Tradable tradable) {
        return !isBsqTradeFee(tradable);
    }

    public Coin getTotalTradeFee(List<Tradable> tradableList, boolean expectBtcFee) {
        return Coin.valueOf(tradableList.stream()
                .mapToLong(tradable -> getTradeFee(tradable, expectBtcFee))
                .sum());
    }

    public long getTradeFee(Tradable tradable, boolean expectBtcFee) {
        return expectBtcFee ? getBtcTradeFee(tradable) : getBsqTradeFee(tradable);
    }

    public int getNumPastTrades(Tradable tradable) {
        if (isOpenOffer(tradable)) {
            return 0;
        }
        NodeAddress addressInTrade = castToTradeModel(tradable).getTradingPeerNodeAddress();
        return (int) getTradeModelStream()
                .map(TradeModel::getTradingPeerNodeAddress)
                .filter(Objects::nonNull)
                .filter(address -> address.equals(addressInTrade))
                .count();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Stream<TradeModel> getTradeModelStream() {
        return Stream.concat(bsqSwapTradeManager.getConfirmedBsqSwapTrades(),
                closedTradableManager.getClosedTrades().stream());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fee utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBtcTradeFee(Tradable tradable) {
        if (isBsqSwapTrade(tradable) || isBsqTradeFee(tradable)) {
            return 0L;
        }
        return isMaker(tradable) ?
                tradable.getOptionalMakerFee().orElse(Coin.ZERO).value :
                tradable.getOptionalTakerFee().orElse(Coin.ZERO).value;
    }

    public long getBsqTradeFee(Tradable tradable) {
        if (isBsqSwapTrade(tradable) || isBsqTradeFee(tradable)) {
            return isMaker(tradable) ?
                    tradable.getOptionalMakerFee().orElse(Coin.ZERO).value :
                    tradable.getOptionalTakerFee().orElse(Coin.ZERO).value;
        }
        return 0L;
    }

    public boolean isBsqTradeFee(Tradable tradable) {
        if (isBsqSwapTrade(tradable)) {
            return true;
        }

        if (isMaker(tradable)) {
            return !tradable.getOffer().isCurrencyForMakerFeeBtc();
        }

        String feeTxId = castToTrade(tradable).getTakerFeeTxId();
        return bsqWalletService.getTransaction(feeTxId) != null;
    }

    public Coin getTxFee(Tradable tradable) {
        return tradable.getOptionalTxFee().orElse(Coin.ZERO);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isOpenOffer(Tradable tradable) {
        return tradable instanceof OpenOffer;
    }

    public boolean isMaker(Tradable tradable) {
        return tradable instanceof MakerTrade || tradable.getOffer().isMyOffer(keyRing);
    }

    public boolean isTakerTrade(Tradable tradable) {
        return tradable instanceof TakerTrade;
    }

    public boolean isBsqSwapTrade(Tradable tradable) {
        return tradable instanceof BsqSwapTrade;
    }

    public boolean isBisqV1Trade(Tradable tradable) {
        return tradable instanceof Trade;
    }

    public Trade castToTrade(Tradable tradable) {
        return (Trade) tradable;
    }

    public TradeModel castToTradeModel(Tradable tradable) {
        return (TradeModel) tradable;
    }

    public BsqSwapTrade castToBsqSwapTrade(Tradable tradable) {
        return (BsqSwapTrade) tradable;
    }
}
