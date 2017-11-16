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

package io.bisq.gui.main.market.trades;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.bisq.common.GlobalSettings;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.util.MathUtils;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.statistics.TradeStatistics2;
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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.scene.chart.XYChart;
import javafx.util.Pair;
import org.bitcoinj.core.Coin;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
    private PriceFeedService priceFeedService;
    private Navigation navigation;
    private BSFormatter formatter;

    private final SetChangeListener<TradeStatistics2> setChangeListener;
    final ObjectProperty<TradeCurrency> selectedTradeCurrencyProperty = new SimpleObjectProperty<>();
    final BooleanProperty showAllTradeCurrenciesProperty = new SimpleBooleanProperty(false);
    private final ObservableList<CurrencyListItem> currencyListItems = FXCollections.observableArrayList();
    private final CurrencyListItem showAllCurrencyListItem = new CurrencyListItem(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, GUIUtil.SHOW_ALL_FLAG), -1);
    final ObservableList<TradeStatistics2> tradeStatisticsByCurrency = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> priceItems = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> volumeItems = FXCollections.observableArrayList();
    private Map<Long, Pair<Date, Set<TradeStatistics2>>> itemsPerInterval;

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
            selectedTradeCurrencyProperty.set(GlobalSettings.getDefaultTradeCurrency());

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
                //noinspection unchecked
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
            } else {
                boolean showAllEntry = isShowAllEntry(code);
                showAllTradeCurrenciesProperty.set(showAllEntry);
                if (!showAllEntry) {
                    selectedTradeCurrencyProperty.set(tradeCurrency);
                    preferences.setTradeChartsScreenCurrencyCode(code);
                }

                updateChartData();

                if (showAllEntry)
                    priceFeedService.setCurrencyCode(GlobalSettings.getDefaultTradeCurrency().getCode());
                else
                    priceFeedService.setCurrencyCode(code);
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
        tradeStatisticsByCurrency.setAll(tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> showAllTradeCurrenciesProperty.get() || e.getCurrencyCode().equals(getCurrencyCode()))
                .collect(Collectors.toList()));

        // Generate date range and create sets for all ticks
        itemsPerInterval = new HashMap<>();
        Date time = new Date();
        for (long i = maxTicks + 1; i >= 0; --i) {
            Set<TradeStatistics2> set = new HashSet<>();
            Pair<Date, Set<TradeStatistics2>> pair = new Pair<>((Date) time.clone(), set);
            itemsPerInterval.put(i, pair);
            time.setTime(time.getTime() - 1);
            time = roundToTick(time,  tickUnit);
        }

        // Get all entries for the defined time interval
        tradeStatisticsByCurrency.stream().forEach(e -> {
            for (long i = maxTicks; i > 0; --i) {
                Pair<Date, Set<TradeStatistics2>> p = itemsPerInterval.get(i);
                if (e.getTradeDate().after(p.getKey())) {
                    p.getValue().add(e);
                    break;
                }
            }
        });

        // create CandleData for defined time interval
        List<CandleData> candleDataList = itemsPerInterval.entrySet().stream()
                .filter(entry -> entry.getKey() >= 0 && !entry.getValue().getValue().isEmpty())
                .map(entry -> getCandleData(entry.getKey(), entry.getValue().getValue()))
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
    CandleData getCandleData(long tick, Set<TradeStatistics2> set) {
        long open = 0;
        long close = 0;
        long high = 0;
        long low = 0;
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;
        long numTrades = set.size();

        for (TradeStatistics2 item : set) {
            long tradePriceAsLong = item.getTradePrice().getValue();
            if (CurrencyUtil.isCryptoCurrency(getCurrencyCode())) {
                low = (low != 0) ? Math.max(low, tradePriceAsLong) : tradePriceAsLong;
                high = (high != 0) ? Math.min(high, tradePriceAsLong) : tradePriceAsLong;
            } else {
                low = (low != 0) ? Math.min(low, tradePriceAsLong) : tradePriceAsLong;
                high = (high != 0) ? Math.max(high, tradePriceAsLong) : tradePriceAsLong;
            }

            accumulatedVolume += (item.getTradeVolume() != null) ? item.getTradeVolume().getValue() : 0;
            accumulatedAmount += item.getTradeAmount().getValue();
        }

        List<TradeStatistics2> list = new ArrayList<>(set);
        list.sort((o1, o2) -> (o1.getTradeDate().getTime() < o2.getTradeDate().getTime() ? -1 : (o1.getTradeDate().getTime() == o2.getTradeDate().getTime() ? 0 : 1)));
        if (list.size() > 0) {
            open = list.get(0).getTradePrice().getValue();
            close = list.get(list.size() - 1).getTradePrice().getValue();
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

        final Date dateFrom = new Date(getTimeFromTickIndex(tick));
        final Date dateTo = new Date(getTimeFromTickIndex(tick + 1));
        String dateString = tickUnit.ordinal() > TickUnit.DAY.ordinal() ?
                formatter.formatDateTimeSpan(dateFrom, dateTo) :
                formatter.formatDate(dateFrom) + " - " + formatter.formatDate(dateTo);
        return new CandleData(tick, open, close, high, low, averagePrice, accumulatedAmount, accumulatedVolume,
                numTrades, isBullish, dateString);
    }

    Date roundToTick(Date time, TickUnit tickUnit) {
        ZonedDateTime zdt = time.toInstant().atZone(ZoneId.systemDefault());
        LocalDateTime tradeLocal = zdt.toLocalDateTime();

        switch (tickUnit) {
            case YEAR:
                return Date.from(tradeLocal.withMonth(1).withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
            case MONTH:
                return Date.from(tradeLocal.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
            case WEEK:
                int dayOfWeek = tradeLocal.getDayOfWeek().getValue();
                LocalDateTime firstDayOfWeek = ChronoUnit.DAYS.addTo(tradeLocal, 1 - dayOfWeek);
                return Date.from(firstDayOfWeek.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
            case DAY:
                return Date.from(tradeLocal.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
            case HOUR:
                return Date.from(tradeLocal.withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
            case MINUTE_10:
                return Date.from(tradeLocal.withMinute(tradeLocal.getMinute() - tradeLocal.getMinute() % 10).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant());
            default:
                return Date.from(tradeLocal.atZone(ZoneId.systemDefault()).toInstant());
        }
    }

    private long getTimeFromTick(long tick) {
        if (itemsPerInterval == null || itemsPerInterval.get(tick) == null) return 0;
        return itemsPerInterval.get(tick).getKey().getTime();
    }

    long getTimeFromTickIndex(long index) {
        if (index > maxTicks + 1) return 0;
        return getTimeFromTick(index);
    }

    private boolean isShowAllEntry(String id) {
        return id.equals(GUIUtil.SHOW_ALL_FLAG);
    }

    private boolean isEditEntry(String id) {
        return id.equals(GUIUtil.EDIT_FLAG);
    }
}
