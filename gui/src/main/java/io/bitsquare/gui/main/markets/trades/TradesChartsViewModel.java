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
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.markets.trades.charts.CandleData;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.statistics.TradeStatistics;
import io.bitsquare.trade.statistics.TradeStatisticsManager;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.scene.chart.XYChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class TradesChartsViewModel extends ActivatableViewModel {
    private static final Logger log = LoggerFactory.getLogger(TradesChartsViewModel.class);
    private static final int TAB_INDEX = 2;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum TickUnit {
        MONTH,
        WEEK,
        DAY,
        HOUR,
        MINUTE_10,
        MINUTE
    }

    private final TradeStatisticsManager tradeStatisticsManager;
    final Preferences preferences;
    private PriceFeedService priceFeedService;

    private final SetChangeListener<TradeStatistics> setChangeListener;
    final ObjectProperty<TradeCurrency> tradeCurrencyProperty = new SimpleObjectProperty<>();

    final ObservableList<TradeStatistics> tradeStatisticsByCurrency = FXCollections.observableArrayList();
    ObservableList<XYChart.Data<Number, Number>> priceItems = FXCollections.observableArrayList();
    ObservableList<XYChart.Data<Number, Number>> volumeItems = FXCollections.observableArrayList();

    TickUnit tickUnit = TickUnit.MONTH;
    int maxTicks = 30;
    private int selectedTabIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradesChartsViewModel(TradeStatisticsManager tradeStatisticsManager, Preferences preferences, PriceFeedService priceFeedService) {
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;

        setChangeListener = change -> updateChartData();

        Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(preferences.getTradeStatisticsScreenCurrencyCode());
        if (tradeCurrencyOptional.isPresent())
            tradeCurrencyProperty.set(tradeCurrencyOptional.get());
        else {
            tradeCurrencyProperty.set(CurrencyUtil.getDefaultTradeCurrency());
        }

        tickUnit = TickUnit.values()[preferences.getTradeStatisticsTickUnitIndex()];
    }

    @VisibleForTesting
    TradesChartsViewModel() {
        setChangeListener = null;
        preferences = null;
        tradeStatisticsManager = null;
    }


    @Override
    protected void activate() {
        tradeStatisticsManager.getObservableTradeStatisticsSet().addListener(setChangeListener);
        updateChartData();
        syncPriceFeedCurrency();
    }

    @Override
    protected void deactivate() {
        tradeStatisticsManager.getObservableTradeStatisticsSet().removeListener(setChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        this.tradeCurrencyProperty.set(tradeCurrency);
        preferences.setTradeStatisticsScreenCurrencyCode(tradeCurrency.getCode());
        updateChartData();

        if (!preferences.getUseStickyMarketPrice())
            priceFeedService.setCurrencyCode(tradeCurrency.getCode());
    }

    void setTickUnit(TickUnit tickUnit) {
        this.tickUnit = tickUnit;
        preferences.setTradeStatisticsTickUnitIndex(tickUnit.ordinal());
        updateChartData();
    }

    void setSelectedTabIndex(int selectedTabIndex) {
        this.selectedTabIndex = selectedTabIndex;
        syncPriceFeedCurrency();
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

    private void syncPriceFeedCurrency() {
        if (!preferences.getUseStickyMarketPrice() && selectedTabIndex == TAB_INDEX)
            priceFeedService.setCurrencyCode(tradeCurrencyProperty.get().getCode());
    }

    private void updateChartData() {
        tradeStatisticsByCurrency.setAll(tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> e.currency.equals(getCurrencyCode()))
                .collect(Collectors.toList()));

        // Get all entries for the defined time interval
        Map<Long, Set<TradeStatistics>> itemsPerInterval = new HashMap<>();
        tradeStatisticsByCurrency.stream().forEach(e -> {
            Set<TradeStatistics> set;
            final long time = getTickFromTime(e.tradeDate, tickUnit);
            final long now = getTickFromTime(new Date().getTime(), tickUnit);
            long index = maxTicks - (now - time);
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

    @VisibleForTesting
    CandleData getCandleData(long tick, Set<TradeStatistics> set) {
        long open = 0;
        long close = 0;
        long high = 0;
        long low = 0;
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;

        for (TradeStatistics item : set) {
            final long tradePriceAsLong = item.tradePrice;
            low = (low != 0) ? Math.min(low, tradePriceAsLong) : tradePriceAsLong;
            high = (high != 0) ? Math.max(high, tradePriceAsLong) : tradePriceAsLong;
            accumulatedVolume += (item.getTradeVolume() != null) ? item.getTradeVolume().value : 0;
            accumulatedAmount += item.tradeAmount;
        }
        // 100000000 -> Coin.COIN.value;
        long averagePrice = Math.round((double) accumulatedVolume * 100000000d / (double) accumulatedAmount);

        List<TradeStatistics> list = new ArrayList<>(set);
        list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? -1 : (o1.tradeDate == o2.tradeDate ? 0 : 1)));
        if (list.size() > 0) {
            open = list.get(0).tradePrice;
            close = list.get(list.size() - 1).tradePrice;
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

    long getTimeFromTickIndex(long index) {
        long now = getTickFromTime(new Date().getTime(), tickUnit);
        long tick = now - (maxTicks - index);
        return getTimeFromTick(tick, tickUnit);
    }
}
