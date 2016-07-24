/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.markets.trades;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.markets.trades.charts.CandleData;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.trade.TradeStatistics;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class TradesChartsViewModel extends ActivatableViewModel {
    private static final Logger log = LoggerFactory.getLogger(TradesChartsViewModel.class);

    public enum TickUnit {
        MONTH,
        WEEK,
        DAY,
        HOUR,
        MINUTE_10,
        MINUTE
    }

    private final Preferences preferences;
    private P2PService p2PService;

    private final HashMapChangedListener mapChangedListener;
    final ObjectProperty<TradeCurrency> tradeCurrencyProperty = new SimpleObjectProperty<>();

    private final Set<TradeStatistics> allTradeStatistics = new HashSet<>();
    final ObservableList<TradeStatistics> tradeStatisticsByCurrency = FXCollections.observableArrayList();
    ObservableList<XYChart.Data<Number, Number>> priceItems = FXCollections.observableArrayList();
    ObservableList<XYChart.Data<Number, Number>> volumeItems = FXCollections.observableArrayList();

    TickUnit tickUnit;
    int upperBound = 30;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradesChartsViewModel(P2PService p2PService, Preferences preferences) {
        this.p2PService = p2PService;
        this.preferences = preferences;

        mapChangedListener = new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                addItem(data.getStoragePayload(), true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                final StoragePayload storagePayload = data.getStoragePayload();
                if (storagePayload instanceof TradeStatistics && allTradeStatistics.contains(storagePayload)) {
                    allTradeStatistics.remove(storagePayload);
                    updateChartData();
                }
            }
        };

        Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(preferences.getTradeStatisticsScreenCurrencyCode());
        if (tradeCurrencyOptional.isPresent())
            tradeCurrencyProperty.set(tradeCurrencyOptional.get());
        else {
            tradeCurrencyProperty.set(CurrencyUtil.getDefaultTradeCurrency());
        }

        tickUnit = TickUnit.values()[preferences.getTradeStatisticsTickUnit()];
    }

    @VisibleForTesting
    TradesChartsViewModel() {
        mapChangedListener = null;
        preferences = null;
    }


    @Override
    protected void activate() {
        p2PService.addHashSetChangedListener(mapChangedListener);
        p2PService.getDataMap().entrySet().stream().forEach(e -> addItem(e.getValue().getStoragePayload(), false));
        updateChartData();
    }

    @Override
    protected void deactivate() {
        p2PService.removeHashMapChangedListener(mapChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        this.tradeCurrencyProperty.set(tradeCurrency);
        preferences.setTradeStatisticsScreenCurrencyCode(tradeCurrency.getCode());
        updateChartData();
    }

    public void setTickUnit(TickUnit tickUnit) {
        this.tickUnit = tickUnit;
        preferences.setTradeStatisticsTickUnit(tickUnit.ordinal());
        updateChartData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getCurrencyCode() {
        return tradeCurrencyProperty.get().getCode();
    }

    public ObservableList<TradeCurrency> getTradeCurrencies() {
        return preferences.getTradeCurrenciesAsObservable();
    }

    public TradeCurrency getTradeCurrency() {
        return tradeCurrencyProperty.get();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////
    private void addItem(StoragePayload storagePayload, boolean doUpdate) {
        if (storagePayload instanceof TradeStatistics && !allTradeStatistics.contains(storagePayload)) {
            allTradeStatistics.add((TradeStatistics) storagePayload);
            if (doUpdate)
                updateChartData();
        }
    }


    private void updateChartData() {
        tradeStatisticsByCurrency.setAll(allTradeStatistics.stream()
                .filter(e -> e.offer.getCurrencyCode().equals(getCurrencyCode()))
                .collect(Collectors.toList()));

        // Get all entries for the defined time interval
        Map<Long, Set<TradeStatistics>> itemsPerInterval = new HashMap<>();
        tradeStatisticsByCurrency.stream().forEach(e -> {
            Set<TradeStatistics> set;
            final long time = getTickFromTime(e.tradeDateAsTime, tickUnit);
            final long now = getTickFromTime(new Date().getTime(), tickUnit);
            long index = upperBound - (now - time);
            if (itemsPerInterval.containsKey(index)) {
                set = itemsPerInterval.get(index);
            } else {
                set = new HashSet<>();
                itemsPerInterval.put(index, set);
            }
            set.add(e);
        });

        // create CandleData for defined time interval
        List<CandleData> candleDataList = itemsPerInterval.entrySet().stream()
                .map(entry -> getCandleData(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        candleDataList.sort((o1, o2) -> (o1.tick < o2.tick ? -1 : (o1.tick == o2.tick ? 0 : 1)));

        priceItems.setAll(candleDataList.stream()
                .map(e -> new XYChart.Data<Number, Number>(e.tick, e.open, e))
                .collect(Collectors.toList()));

        volumeItems.setAll(candleDataList.stream()
                .map(e -> new XYChart.Data<Number, Number>(e.tick, e.accumulatedAmount, e))
                .collect(Collectors.toList()));
    }

    CandleData getCandleData(long tick, Set<TradeStatistics> set) {
        long open = 0;
        long close = 0;
        long high = 0;
        long low = 0;
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;

        for (TradeStatistics item : set) {
            final long tradePriceAsLong = item.tradePriceAsLong;
            low = (low != 0) ? Math.min(low, tradePriceAsLong) : tradePriceAsLong;
            high = (high != 0) ? Math.max(high, tradePriceAsLong) : tradePriceAsLong;
            accumulatedVolume += item.getTradeVolume().value;
            accumulatedAmount += item.tradeAmountAsLong;
        }
        long averagePrice = Math.round(accumulatedVolume * Coin.COIN.value / accumulatedAmount);

        List<TradeStatistics> list = new ArrayList<>(set);
        list.sort((o1, o2) -> (o1.tradeDateAsTime < o2.tradeDateAsTime ? -1 : (o1.tradeDateAsTime == o2.tradeDateAsTime ? 0 : 1)));
        if (list.size() > 0) {
            open = list.get(0).tradePriceAsLong;
            close = list.get(list.size() - 1).tradePriceAsLong;
        }
        boolean isBullish = close > open;
        return new CandleData(tick, open, close, high, low, averagePrice, accumulatedAmount, accumulatedVolume, isBullish);

    }

    long getTickFromTime(long tradeDateAsTime, TickUnit tickUnit) {
        switch (tickUnit) {
            case MONTH:
                return TimeUnit.MILLISECONDS.toDays(tradeDateAsTime) / 31;
            case WEEK:
                return TimeUnit.MILLISECONDS.toDays(tradeDateAsTime) / 7;
            case DAY:
                return TimeUnit.MILLISECONDS.toDays(tradeDateAsTime);
            case HOUR:
                return TimeUnit.MILLISECONDS.toHours(tradeDateAsTime);
            case MINUTE_10:
                return TimeUnit.MILLISECONDS.toMinutes(tradeDateAsTime) / 10;
            case MINUTE:
                return TimeUnit.MILLISECONDS.toMinutes(tradeDateAsTime);
            default:
                return tradeDateAsTime;
        }
    }

    long getTimeFromTick(long tick, TickUnit tickUnit) {
        switch (tickUnit) {
            case MONTH:
                return TimeUnit.DAYS.toMillis(tick) * 31;
            case WEEK:
                return TimeUnit.DAYS.toMillis(tick) * 7;
            case DAY:
                return TimeUnit.DAYS.toMillis(tick);
            case HOUR:
                return TimeUnit.HOURS.toMillis(tick);
            case MINUTE_10:
                return TimeUnit.MINUTES.toMillis(tick) * 10;
            case MINUTE:
                return TimeUnit.MINUTES.toMillis(tick);
            default:
                return tick;
        }
    }

}
