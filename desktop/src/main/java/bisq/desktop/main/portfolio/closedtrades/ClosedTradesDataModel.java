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

import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.main.PriceUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOffer;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.AveragePriceUtil;
import bisq.core.util.VolumeUtil;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

class ClosedTradesDataModel extends ActivatableDataModel {

    final ClosedTradableManager closedTradableManager;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final PriceFeedService priceFeedService;
    private final ObservableList<ClosedTradableListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Tradable> tradesListChangeListener;

    @Inject
    public ClosedTradesDataModel(ClosedTradableManager closedTradableManager,
                                 BsqWalletService bsqWalletService,
                                 Preferences preferences,
                                 TradeStatisticsManager tradeStatisticsManager,
                                 PriceFeedService priceFeedService) {
        this.closedTradableManager = closedTradableManager;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.priceFeedService = priceFeedService;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    protected void activate() {
        applyList();
        closedTradableManager.getObservableList().addListener(tradesListChangeListener);
    }

    @Override
    protected void deactivate() {
        closedTradableManager.getObservableList().removeListener(tradesListChangeListener);
    }

    public ObservableList<ClosedTradableListItem> getList() {
        return list;
    }

    public OfferPayload.Direction getDirection(Offer offer) {
        return closedTradableManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(closedTradableManager.getObservableList().stream().map(ClosedTradableListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTradable().getDate().compareTo(o1.getTradable().getDate()));
    }

    boolean wasMyOffer(Tradable tradable) {
        return closedTradableManager.wasMyOffer(tradable.getOffer());
    }

    Coin getTotalAmount() {
        return Coin.valueOf(getList().stream()
                .map(ClosedTradableListItem::getTradable)
                .filter(e -> e instanceof Trade)
                .map(e -> (Trade) e)
                .mapToLong(Trade::getTradeAmountAsLong)
                .sum());
    }

    Map<String, Long> getTotalVolumeByCurrency() {
        Map<String, Long> map = new HashMap<>();
        getList().stream()
                .map(ClosedTradableListItem::getTradable)
                .filter(e -> e instanceof Trade)
                .map(e -> (Trade) e)
                .map(Trade::getTradeVolume)
                .filter(Objects::nonNull)
                .forEach(volume -> {
                    String currencyCode = volume.getCurrencyCode();
                    map.putIfAbsent(currencyCode, 0L);
                    map.put(currencyCode, volume.getValue() + map.get(currencyCode));
                });
        return map;
    }

    public Optional<Volume> getVolumeInUserFiatCurrency(Coin amount) {
        return getVolume(amount, preferences.getPreferredTradeCurrency().getCode());
    }

    public Optional<Volume> getVolume(Coin amount, String currencyCode) {
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        if (marketPrice == null) {
            return Optional.empty();
        }

        Price price = PriceUtil.marketPriceToPrice(marketPrice);
        return Optional.of(VolumeUtil.getVolume(amount, price));
    }

    public Volume getBsqVolumeInUsdWithAveragePrice(Coin amount) {
        Tuple2<Price, Price> tuple = AveragePriceUtil.getAveragePriceTuple(preferences, tradeStatisticsManager, 30);
        Price usdPrice = tuple.first;
        long value = Math.round(amount.value * usdPrice.getValue() / 100d);
        return new Volume(Fiat.valueOf("USD", value));
    }

    public Coin getTotalTxFee() {
        return Coin.valueOf(getList().stream()
                .map(ClosedTradableListItem::getTradable)
                .mapToLong(tradable -> {
                    if (wasMyOffer(tradable) || tradable instanceof OpenOffer) {
                        return tradable.getOffer().getTxFee().value;
                    } else {
                        // taker pays for 3 transactions
                        return ((Trade) tradable).getTxFee().multiply(3).value;
                    }
                })
                .sum());
    }

    public Coin getTotalTradeFee(boolean expectBtcFee) {
        return Coin.valueOf(getList().stream()
                .map(ClosedTradableListItem::getTradable)
                .mapToLong(tradable -> getTradeFee(tradable, expectBtcFee))
                .sum());
    }

    protected long getTradeFee(Tradable tradable, boolean expectBtcFee) {
        Offer offer = tradable.getOffer();
        if (wasMyOffer(tradable) || tradable instanceof OpenOffer) {
            String makerFeeTxId = offer.getOfferFeePaymentTxId();
            boolean notInBsqWallet = bsqWalletService.getTransaction(makerFeeTxId) == null;
            if (expectBtcFee) {
                if (notInBsqWallet) {
                    return offer.getMakerFee().value;
                } else {
                    return 0;
                }
            } else {
                if (notInBsqWallet) {
                    return 0;
                } else {
                    return offer.getMakerFee().value;
                }
            }
        } else {
            Trade trade = (Trade) tradable;
            String takerFeeTxId = trade.getTakerFeeTxId();
            boolean notInBsqWallet = bsqWalletService.getTransaction(takerFeeTxId) == null;
            if (expectBtcFee) {
                if (notInBsqWallet) {
                    return trade.getTakerFee().value;
                } else {
                    return 0;
                }
            } else {
                if (notInBsqWallet) {
                    return 0;
                } else {
                    return trade.getTakerFee().value;
                }
            }
        }
    }
}
