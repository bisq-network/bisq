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

package bisq.desktop.main.market.trades;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ActivatableViewModel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.market.trades.charts.CandleData;
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;
import bisq.desktop.util.CurrencyList;
import bisq.desktop.util.CurrencyListItem;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Altcoin;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import bisq.common.UserThread;
import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import javafx.scene.chart.XYChart;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

class TradesChartsViewModel extends ActivatableViewModel {

    private static final int TAB_INDEX = 2;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum TickUnit {
        YEAR,
        MONTH,
        WEEK,
        DAY,
        HOUR,
        MINUTE_10
    }

    private final TradeStatisticsManager tradeStatisticsManager;
    final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final Navigation navigation;

    private final SetChangeListener<TradeStatistics3> setChangeListener;
    final ObjectProperty<TradeCurrency> selectedTradeCurrencyProperty = new SimpleObjectProperty<>();
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty(false);
    private final CurrencyList currencyListItems;
    private final CurrencyListItem showAllCurrencyListItem = new CurrencyListItem(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, ""), -1);
    final ObservableList<TradeStatistics3> selectedTradeStatistics = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> priceItems = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> volumeItems = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> volumeInUsdItems = FXCollections.observableArrayList();
    private Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval;

    TickUnit tickUnit;
    final int maxTicks = 90;
    private int selectedTabIndex;
    final Map<TickUnit, Map<Long, Long>> usdAveragePriceMapsPerTickUnit = new HashMap<>();
    private boolean fillTradeCurrenciesOnActivateCalled;
    final BooleanProperty modelReady = new SimpleBooleanProperty(false);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    TradesChartsViewModel(TradeStatisticsManager tradeStatisticsManager, Preferences preferences,
                          PriceFeedService priceFeedService, Navigation navigation) {
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.navigation = navigation;

        setChangeListener = change -> {
            updateSelectedTradeStatistics(getCurrencyCode());
            updateChartData();
            fillTradeCurrencies();
        };

        String tradeChartsScreenCurrencyCode = preferences.getTradeChartsScreenCurrencyCode();
        showAllTradeCurrenciesProperty.set(isShowAllEntry(tradeChartsScreenCurrencyCode));

        TradeCurrency tradeCurrency = CurrencyUtil.getTradeCurrency(tradeChartsScreenCurrencyCode)
                .orElse(GlobalSettings.getDefaultTradeCurrency());
        selectedTradeCurrencyProperty.set(tradeCurrency);

        tickUnit = TickUnit.values()[preferences.getTradeStatisticsTickUnitIndex()];

        currencyListItems = new CurrencyList(this.preferences);
    }

    @Override
    protected void activate() {
        tradeStatisticsManager.getObservableTradeStatisticsSet().addListener(setChangeListener);
        if (!fillTradeCurrenciesOnActivateCalled) {
            fillTradeCurrencies();
            fillTradeCurrenciesOnActivateCalled = true;
        }
        syncPriceFeedCurrency();
        setMarketPriceFeedCurrency();

        ChartCalculations.buildUsdPricesPerTickUnit(tradeStatisticsManager.getObservableTradeStatisticsSet())
                .whenComplete((usdAveragePriceMapsPerTickUnit, throwable) -> {
                    if (throwable != null) {
                        log.error(throwable.toString());
                        return;
                    }

                    UserThread.execute(() -> {
                        this.usdAveragePriceMapsPerTickUnit.clear();
                        this.usdAveragePriceMapsPerTickUnit.putAll(usdAveragePriceMapsPerTickUnit);

                        updateSelectedTradeStatistics(getCurrencyCode());
                        updateChartData();
                        modelReady.set(true);
                    });
                });
    }

    @Override
    protected void deactivate() {
        tradeStatisticsManager.getObservableTradeStatisticsSet().removeListener(setChangeListener);
        usdAveragePriceMapsPerTickUnit.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            String code = tradeCurrency.getCode();

            if (isEditEntry(code)) {
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
                return;
            }

            boolean showAllEntry = isShowAllEntry(code);
            showAllTradeCurrenciesProperty.set(showAllEntry);
            if (showAllEntry) {
                priceFeedService.setCurrencyCode(GlobalSettings.getDefaultTradeCurrency().getCode());
            } else {
                selectedTradeCurrencyProperty.set(tradeCurrency);
                priceFeedService.setCurrencyCode(code);

            }
            preferences.setTradeChartsScreenCurrencyCode(code);

            updateSelectedTradeStatistics(getCurrencyCode());
            updateChartData();
        }
    }

    void setTickUnit(TickUnit tickUnit) {
        this.tickUnit = tickUnit;
        preferences.setTradeStatisticsTickUnitIndex(tickUnit.ordinal());
        updateChartData();
    }

    void setSelectedTabIndex(int selectedTabIndex) {
        this.selectedTabIndex = selectedTabIndex;
        syncPriceFeedCurrency();
        setMarketPriceFeedCurrency();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getCurrencyCode() {
        return selectedTradeCurrencyProperty.get().getCode();
    }

    public ObservableList<CurrencyListItem> getCurrencyListItems() {
        return currencyListItems.getObservableList();
    }

    public Optional<CurrencyListItem> getSelectedCurrencyListItem() {
        return currencyListItems.getObservableList().stream().filter(e -> e.tradeCurrency.equals(selectedTradeCurrencyProperty.get())).findAny();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillTradeCurrencies() {
        // Don't use a set as we need all entries
        List<TradeCurrency> tradeCurrencyList = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .flatMap(e -> CurrencyUtil.getTradeCurrency(e.getCurrency()).stream())
                .collect(Collectors.toList());

        currencyListItems.updateWithCurrencies(tradeCurrencyList, showAllCurrencyListItem);
    }

    private void setMarketPriceFeedCurrency() {
        if (selectedTabIndex == TAB_INDEX) {
            if (showAllTradeCurrenciesProperty.get())
                priceFeedService.setCurrencyCode(GlobalSettings.getDefaultTradeCurrency().getCode());
            else
                priceFeedService.setCurrencyCode(getCurrencyCode());
        }
    }

    private void syncPriceFeedCurrency() {
        if (selectedTabIndex == TAB_INDEX)
            priceFeedService.setCurrencyCode(selectedTradeCurrencyProperty.get().getCode());
    }

    private void updateChartData() {
        // Generate date range and create sets for all ticks
        itemsPerInterval = new HashMap<>();
        Date time = new Date();
        for (long i = maxTicks + 1; i >= 0; --i) {
            Pair<Date, Set<TradeStatistics3>> pair = new Pair<>((Date) time.clone(), new HashSet<>());
            itemsPerInterval.put(i, pair);
            // We adjust the time for the next iteration
            time.setTime(time.getTime() - 1);
            time = roundToTick(time, tickUnit);
        }

        // Get all entries for the defined time interval
        selectedTradeStatistics.forEach(tradeStatistics -> {
            for (long i = maxTicks; i > 0; --i) {
                Pair<Date, Set<TradeStatistics3>> pair = itemsPerInterval.get(i);
                if (tradeStatistics.getDate().after(pair.getKey())) {
                    pair.getValue().add(tradeStatistics);
                    break;
                }
            }
        });

        Map<Long, Long> usdAveragePriceMap = usdAveragePriceMapsPerTickUnit.get(tickUnit);
        AtomicLong averageUsdPrice = new AtomicLong(0);

        // create CandleData for defined time interval
        List<CandleData> candleDataList = itemsPerInterval.entrySet().stream()
                .filter(entry -> entry.getKey() >= 0 && !entry.getValue().getValue().isEmpty())
                .map(entry -> {
                    long tickStartDate = entry.getValue().getKey().getTime();
                    // If we don't have a price we take the previous one
                    if (usdAveragePriceMap.containsKey(tickStartDate)) {
                        averageUsdPrice.set(usdAveragePriceMap.get(tickStartDate));
                    }
                    return getCandleData(entry.getKey(), entry.getValue().getValue(), averageUsdPrice.get());
                })
                .sorted(Comparator.comparingLong(o -> o.tick))
                .collect(Collectors.toList());

        priceItems.setAll(candleDataList.stream()
                .map(e -> new XYChart.Data<Number, Number>(e.tick, e.open, e))
                .collect(Collectors.toList()));

        volumeItems.setAll(candleDataList.stream()
                .map(candleData -> new XYChart.Data<Number, Number>(candleData.tick, candleData.accumulatedAmount, candleData))
                .collect(Collectors.toList()));

        volumeInUsdItems.setAll(candleDataList.stream()
                .map(candleData -> new XYChart.Data<Number, Number>(candleData.tick, candleData.volumeInUsd, candleData))
                .collect(Collectors.toList()));
    }

    private void updateSelectedTradeStatistics(String currencyCode) {
        selectedTradeStatistics.setAll(tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> showAllTradeCurrenciesProperty.get() || e.getCurrency().equals(currencyCode))
                .collect(Collectors.toList()));
    }

    @VisibleForTesting
    CandleData getCandleData(long tick, Set<TradeStatistics3> set, long averageUsdPrice) {
        long open = 0;
        long close = 0;
        long high = 0;
        long low = 0;
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;
        long numTrades = set.size();
        List<Long> tradePrices = new ArrayList<>();
        for (TradeStatistics3 item : set) {
            long tradePriceAsLong = item.getTradePrice().getValue();
            // Previously a check was done which inverted the low and high for cryptocurrencies.
            low = (low != 0) ? Math.min(low, tradePriceAsLong) : tradePriceAsLong;
            high = (high != 0) ? Math.max(high, tradePriceAsLong) : tradePriceAsLong;

            accumulatedVolume += item.getTradeVolume().getValue();
            accumulatedAmount += item.getTradeAmount().getValue();
            tradePrices.add(tradePriceAsLong);
        }
        Collections.sort(tradePrices);

        List<TradeStatistics3> list = new ArrayList<>(set);
        list.sort(Comparator.comparingLong(TradeStatistics3::getDateAsLong));
        if (list.size() > 0) {
            open = list.get(0).getTradePrice().getValue();
            close = list.get(list.size() - 1).getTradePrice().getValue();
        }

        long averagePrice;
        Long[] prices = new Long[tradePrices.size()];
        tradePrices.toArray(prices);
        long medianPrice = MathUtils.getMedian(prices);
        boolean isBullish;
        if (CurrencyUtil.isCryptoCurrency(getCurrencyCode())) {
            isBullish = close < open;
            double accumulatedAmountAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedAmount, Altcoin.SMALLEST_UNIT_EXPONENT);
            averagePrice = MathUtils.roundDoubleToLong(accumulatedAmountAsDouble / (double) accumulatedVolume);
        } else {
            isBullish = close > open;
            double accumulatedVolumeAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedVolume, Coin.SMALLEST_UNIT_EXPONENT);
            averagePrice = MathUtils.roundDoubleToLong(accumulatedVolumeAsDouble / (double) accumulatedAmount);
        }

        Date dateFrom = new Date(getTimeFromTickIndex(tick));
        Date dateTo = new Date(getTimeFromTickIndex(tick + 1));
        String dateString = tickUnit.ordinal() > TickUnit.DAY.ordinal() ?
                DisplayUtils.formatDateTimeSpan(dateFrom, dateTo) :
                DisplayUtils.formatDate(dateFrom) + " - " + DisplayUtils.formatDate(dateTo);

        // We do not need precision, so we scale down before multiplication otherwise we could get an overflow.
        averageUsdPrice = (long) MathUtils.scaleDownByPowerOf10((double) averageUsdPrice, 4);
        long volumeInUsd = averageUsdPrice * (long) MathUtils.scaleDownByPowerOf10((double) accumulatedAmount, 4);
        // We store USD value without decimals as its only total volume, no precision is needed.
        volumeInUsd = (long) MathUtils.scaleDownByPowerOf10((double) volumeInUsd, 4);
        return new CandleData(tick, open, close, high, low, averagePrice, medianPrice, accumulatedAmount, accumulatedVolume,
                numTrades, isBullish, dateString, volumeInUsd);
    }

    Date roundToTick(Date time, TickUnit tickUnit) {
        return ChartCalculations.roundToTick(time.toInstant().atZone(ChartCalculations.ZONE_ID).toLocalDateTime(), tickUnit);
    }

    private long getTimeFromTick(long tick) {
        if (itemsPerInterval == null || itemsPerInterval.get(tick) == null) return 0;
        return itemsPerInterval.get(tick).getKey().getTime();
    }

    long getTimeFromTickIndex(long index) {
        if (index > maxTicks + 1) return 0;
        return getTimeFromTick(index);
    }

    private boolean isShowAllEntry(@Nullable String id) {
        return id != null && id.equals(GUIUtil.SHOW_ALL_FLAG);
    }

    private boolean isEditEntry(@Nullable String id) {
        return id != null && id.equals(GUIUtil.EDIT_FLAG);
    }
}
