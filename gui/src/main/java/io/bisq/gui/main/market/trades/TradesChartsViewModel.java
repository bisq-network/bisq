/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.market.trades;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.util.MathUtils;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.core.user.Preferences;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.model.ActivatableViewModel;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.market.trades.charts.CandleData;
import io.bisq.gui.main.settings.SettingsView;
import io.bisq.gui.main.settings.preferences.PreferencesView;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.CurrencyListItem;
import io.bisq.gui.util.GUIUtil;
import io.bisq.protobuffer.payload.trade.statistics.TradeStatistics;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.scene.chart.XYChart;
import org.bitcoinj.core.Coin;
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
        YEAR,
        MONTH,
        WEEK,
        DAY,
        HOUR,
        MINUTE_10,
        // TODO Can be removed after version 4.9.7
        // Not used anymore but leave it as it might be used in preferences and could cause an exception if not there. 
        MINUTE
    }

    private final TradeStatisticsManager tradeStatisticsManager;
    final Preferences preferences;
    private PriceFeedService priceFeedService;
    private Navigation navigation;
    private BSFormatter formatter;

    private final SetChangeListener<TradeStatistics> setChangeListener;
    final ObjectProperty<TradeCurrency> selectedTradeCurrencyProperty = new SimpleObjectProperty<>();
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty(false);
    private final ObservableList<CurrencyListItem> currencyListItems = FXCollections.observableArrayList();
    private final CurrencyListItem showAllCurrencyListItem = new CurrencyListItem(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, GUIUtil.SHOW_ALL_FLAG), -1);
    final ObservableList<TradeStatistics> tradeStatisticsByCurrency = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> priceItems = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> volumeItems = FXCollections.observableArrayList();

    TickUnit tickUnit = TickUnit.DAY;
    final int maxTicks = 30;
    private int selectedTabIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public TradesChartsViewModel(TradeStatisticsManager tradeStatisticsManager, Preferences preferences, PriceFeedService priceFeedService, Navigation navigation, BSFormatter formatter) {
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.navigation = navigation;
        this.formatter = formatter;

        setChangeListener = change -> {
            updateChartData();
            fillTradeCurrencies();
        };

        Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(preferences.getTradeChartsScreenCurrencyCode());
        if (tradeCurrencyOptional.isPresent())
            selectedTradeCurrencyProperty.set(tradeCurrencyOptional.get());
        else
            selectedTradeCurrencyProperty.set(Preferences.getDefaultTradeCurrency());

        tickUnit = TickUnit.values()[preferences.getTradeStatisticsTickUnitIndex()];
    }

    private void fillTradeCurrencies() {
        // Don't use a set as we need all entries
        List<TradeCurrency> tradeCurrencyList = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .map(e -> {
                    Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(e.getCurrencyCode());
                    if (tradeCurrencyOptional.isPresent())
                        return tradeCurrencyOptional.get();
                    else
                        return null;

                })
                .filter(e -> e != null)
                .collect(Collectors.toList());

        GUIUtil.fillCurrencyListItems(tradeCurrencyList, currencyListItems, showAllCurrencyListItem, preferences);
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
        fillTradeCurrencies();
        updateChartData();
        syncPriceFeedCurrency();
        setMarketPriceFeedCurrency();
    }

    @Override
    protected void deactivate() {
        tradeStatisticsManager.getObservableTradeStatisticsSet().removeListener(setChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            final String code = tradeCurrency.getCode();

            if (isEditEntry(code)) {
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
            } else {
                boolean showAllEntry = isShowAllEntry(code);
                showAllTradeCurrenciesProperty.set(showAllEntry);
                if (!showAllEntry) {
                    selectedTradeCurrencyProperty.set(tradeCurrency);
                    preferences.setTradeChartsScreenCurrencyCode(code);
                }

                updateChartData();

                if (!preferences.getUseStickyMarketPrice()) {
                    if (showAllEntry)
                        priceFeedService.setCurrencyCode(Preferences.getDefaultTradeCurrency().getCode());
                    else
                        priceFeedService.setCurrencyCode(code);
                }
            }
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
        return currencyListItems;
    }

    public Optional<CurrencyListItem> getSelectedCurrencyListItem() {
        return currencyListItems.stream().filter(e -> e.tradeCurrency.equals(selectedTradeCurrencyProperty.get())).findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setMarketPriceFeedCurrency() {
        if (!preferences.getUseStickyMarketPrice() && selectedTabIndex == TAB_INDEX) {
            if (showAllTradeCurrenciesProperty.get())
                priceFeedService.setCurrencyCode(Preferences.getDefaultTradeCurrency().getCode());
            else
                priceFeedService.setCurrencyCode(getCurrencyCode());
        }
    }

    private void syncPriceFeedCurrency() {
        if (!preferences.getUseStickyMarketPrice() && selectedTabIndex == TAB_INDEX)
            priceFeedService.setCurrencyCode(selectedTradeCurrencyProperty.get().getCode());
    }

    private void updateChartData() {
        tradeStatisticsByCurrency.setAll(tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> showAllTradeCurrenciesProperty.get() || e.getCurrencyCode().equals(getCurrencyCode()))
                .collect(Collectors.toList()));

        // Get all entries for the defined time interval
        Map<Long, Set<TradeStatistics>> itemsPerInterval = new HashMap<>();
        final long dateAsTime = new Date().getTime();
        tradeStatisticsByCurrency.stream().forEach(e -> {
            Set<TradeStatistics> set;
            final long time = getTickFromTime(e.tradeDate, tickUnit);
            final long now = getTickFromTime(dateAsTime, tickUnit);
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
                .filter(entry -> entry.getKey() >= 0)
                .map(entry -> getCandleData(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        candleDataList.sort((o1, o2) -> (o1.tick < o2.tick ? -1 : (o1.tick == o2.tick ? 0 : 1)));

        //noinspection Convert2Diamond
        priceItems.setAll(candleDataList.stream()
                .map(e -> new XYChart.Data<Number, Number>(e.tick, e.open, e))
                .collect(Collectors.toList()));

        //noinspection Convert2Diamond
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
        long numTrades = set.size();

        for (TradeStatistics item : set) {
            long tradePriceAsLong = item.tradePrice;
            if (CurrencyUtil.isCryptoCurrency(getCurrencyCode())) {
                low = (low != 0) ? Math.max(low, tradePriceAsLong) : tradePriceAsLong;
                high = (high != 0) ? Math.min(high, tradePriceAsLong) : tradePriceAsLong;
            } else {
                low = (low != 0) ? Math.min(low, tradePriceAsLong) : tradePriceAsLong;
                high = (high != 0) ? Math.max(high, tradePriceAsLong) : tradePriceAsLong;
            }

            accumulatedVolume += (item.getTradeVolume() != null) ? item.getTradeVolume().getValue() : 0;
            accumulatedAmount += item.tradeAmount;
        }

        long averagePrice;
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

        List<TradeStatistics> list = new ArrayList<>(set);
        list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? -1 : (o1.tradeDate == o2.tradeDate ? 0 : 1)));
        if (list.size() > 0) {
            open = list.get(0).tradePrice;
            close = list.get(list.size() - 1).tradePrice;
        }

        final Date dateFrom = new Date(getTimeFromTickIndex(tick));
        final Date dateTo = new Date(getTimeFromTickIndex(tick + 1));
        String dateString = tickUnit.ordinal() > TickUnit.DAY.ordinal() ?
                formatter.formatDateTimeSpan(dateFrom, dateTo) :
                formatter.formatDate(dateFrom) + " - " + formatter.formatDate(dateTo);
        return new CandleData(tick, open, close, high, low, averagePrice, accumulatedAmount, accumulatedVolume,
                numTrades, isBullish, dateString);
    }

    long getTickFromTime(long tradeDateAsTime, TickUnit tickUnit) {
        switch (tickUnit) {
            case YEAR:
                return TimeUnit.MILLISECONDS.toDays(tradeDateAsTime) / 365;
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

    private long getTimeFromTick(long tick, TickUnit tickUnit) {
        switch (tickUnit) {
            case YEAR:
                return TimeUnit.DAYS.toMillis(tick) * 365;
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

    private boolean isShowAllEntry(String id) {
        return id.equals(GUIUtil.SHOW_ALL_FLAG);
    }

    private boolean isEditEntry(String id) {
        return id.equals(GUIUtil.EDIT_FLAG);
    }
}
