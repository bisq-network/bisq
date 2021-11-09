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
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;
import bisq.desktop.util.CurrencyList;
import bisq.desktop.util.CurrencyListItem;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.TradeCurrency;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import bisq.common.UserThread;
import bisq.common.util.CompletableFutureUtils;

import com.google.inject.Inject;

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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

class TradesChartsViewModel extends ActivatableViewModel {
    static final int MAX_TICKS = 90;
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
    final ObservableList<TradeStatistics3> tradeStatisticsByCurrency = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> priceItems = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> volumeItems = FXCollections.observableArrayList();
    final ObservableList<XYChart.Data<Number, Number>> volumeInUsdItems = FXCollections.observableArrayList();
    private final Map<Long, Pair<Date, Set<TradeStatistics3>>> itemsPerInterval = new HashMap<>();

    TickUnit tickUnit;
    private int selectedTabIndex;
    final Map<TickUnit, Map<Long, Long>> usdAveragePriceMapsPerTickUnit = new HashMap<>();
    private boolean fillTradeCurrenciesOnActivateCalled;
    private volatile boolean deactivateCalled;


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
            applyAsyncTradeStatisticsForCurrency(getCurrencyCode())
                    .whenComplete((result, throwable) -> {
                        if (deactivateCalled) {
                            return;
                        }
                        if (throwable != null) {
                            log.error("Error at setChangeListener/applyAsyncTradeStatisticsForCurrency. {}", throwable.toString());
                            return;
                        }
                        applyAsyncChartData();
                    });
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
        long ts = System.currentTimeMillis();
        deactivateCalled = false;

        tradeStatisticsManager.getObservableTradeStatisticsSet().addListener(setChangeListener);
        if (!fillTradeCurrenciesOnActivateCalled) {
            fillTradeCurrencies();
            fillTradeCurrenciesOnActivateCalled = true;
        }
        syncPriceFeedCurrency();
        setMarketPriceFeedCurrency();

        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        CompletableFuture<Boolean> task1Done = new CompletableFuture<>();
        allFutures.add(task1Done);
        CompletableFuture<Boolean> task2Done = new CompletableFuture<>();
        allFutures.add(task2Done);
        CompletableFutureUtils.allOf(allFutures)
                .whenComplete((res, throwable) -> {
                    if (deactivateCalled) {
                        return;
                    }
                    if (throwable != null) {
                        log.error(throwable.toString());
                        return;
                    }
                    //Once applyAsyncUsdAveragePriceMapsPerTickUnit and applyAsyncTradeStatisticsForCurrency are
                    // both completed we call applyAsyncChartData
                    UserThread.execute(this::applyAsyncChartData);
                });

        // We call applyAsyncUsdAveragePriceMapsPerTickUnit and applyAsyncTradeStatisticsForCurrency
        // in parallel for better performance
        applyAsyncUsdAveragePriceMapsPerTickUnit(task1Done);
        applyAsyncTradeStatisticsForCurrency(getCurrencyCode(), task2Done);

        log.debug("activate took {}", System.currentTimeMillis() - ts);
    }

    @Override
    protected void deactivate() {
        deactivateCalled = true;
        tradeStatisticsManager.getObservableTradeStatisticsSet().removeListener(setChangeListener);

        // We want to avoid to trigger listeners in the view so we delay a bit. Deactivate on model is called before
        // deactivate on view.
        UserThread.execute(() -> {
            usdAveragePriceMapsPerTickUnit.clear();
            tradeStatisticsByCurrency.clear();
            priceItems.clear();
            volumeItems.clear();
            volumeInUsdItems.clear();
            itemsPerInterval.clear();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Async calls
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyAsyncUsdAveragePriceMapsPerTickUnit(CompletableFuture<Boolean> completeFuture) {
        long ts = System.currentTimeMillis();
        ChartCalculations.getUsdAveragePriceMapsPerTickUnit(tradeStatisticsManager.getObservableTradeStatisticsSet())
                .whenComplete((usdAveragePriceMapsPerTickUnit, throwable) -> {
                    if (deactivateCalled) {
                        return;
                    }
                    if (throwable != null) {
                        log.error("Error at applyAsyncUsdAveragePriceMapsPerTickUnit. {}", throwable.toString());
                        completeFuture.completeExceptionally(throwable);
                        return;
                    }
                    UserThread.execute(() -> {
                        this.usdAveragePriceMapsPerTickUnit.clear();
                        this.usdAveragePriceMapsPerTickUnit.putAll(usdAveragePriceMapsPerTickUnit);
                        log.debug("applyAsyncUsdAveragePriceMapsPerTickUnit took {}", System.currentTimeMillis() - ts);
                        completeFuture.complete(true);
                    });
                });
    }

    private CompletableFuture<Boolean> applyAsyncTradeStatisticsForCurrency(String currencyCode) {
        return applyAsyncTradeStatisticsForCurrency(currencyCode, null);
    }

    private CompletableFuture<Boolean> applyAsyncTradeStatisticsForCurrency(String currencyCode,
                                                                            @Nullable CompletableFuture<Boolean> completeFuture) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        long ts = System.currentTimeMillis();
        ChartCalculations.getTradeStatisticsForCurrency(tradeStatisticsManager.getObservableTradeStatisticsSet(),
                currencyCode,
                showAllTradeCurrenciesProperty.get())
                .whenComplete((list, throwable) -> {
                    if (deactivateCalled) {
                        return;
                    }
                    if (throwable != null) {
                        log.error("Error at applyAsyncTradeStatisticsForCurrency. {}", throwable.toString());
                        if (completeFuture != null) {
                            completeFuture.completeExceptionally(throwable);
                        }
                        return;
                    }

                    UserThread.execute(() -> {
                        tradeStatisticsByCurrency.setAll(list);
                        log.debug("applyAsyncTradeStatisticsForCurrency took {}", System.currentTimeMillis() - ts);
                        if (completeFuture != null) {
                            completeFuture.complete(true);
                        }
                        future.complete(true);
                    });
                });
        return future;
    }

    private void applyAsyncChartData() {
        long ts = System.currentTimeMillis();
        ChartCalculations.getUpdateChartResult(new ArrayList<>(tradeStatisticsByCurrency),
                tickUnit,
                usdAveragePriceMapsPerTickUnit,
                getCurrencyCode())
                .whenComplete((updateChartResult, throwable) -> {
                    if (deactivateCalled) {
                        return;
                    }
                    if (throwable != null) {
                        log.error("Error at applyAsyncChartData. {}", throwable.toString());
                        return;
                    }
                    UserThread.execute(() -> {
                        itemsPerInterval.clear();
                        itemsPerInterval.putAll(updateChartResult.getItemsPerInterval());

                        priceItems.setAll(updateChartResult.getPriceItems());
                        volumeItems.setAll(updateChartResult.getVolumeItems());
                        volumeInUsdItems.setAll(updateChartResult.getVolumeInUsdItems());
                        log.debug("applyAsyncChartData took {}", System.currentTimeMillis() - ts);
                    });
                });
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

            applyAsyncTradeStatisticsForCurrency(getCurrencyCode())
                    .whenComplete((result, throwable) -> {
                        if (deactivateCalled) {
                            return;
                        }
                        if (throwable != null) {
                            log.error("Error at onSetTradeCurrency/applyAsyncTradeStatisticsForCurrency. {}", throwable.toString());
                            return;
                        }
                        applyAsyncChartData();
                    });
        }
    }

    void setTickUnit(TickUnit tickUnit) {
        this.tickUnit = tickUnit;
        preferences.setTradeStatisticsTickUnitIndex(tickUnit.ordinal());
        applyAsyncChartData();
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

    long getTimeFromTickIndex(long tick) {
        return ChartCalculations.getTimeFromTickIndex(tick, itemsPerInterval);
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

    private boolean isShowAllEntry(@Nullable String id) {
        return id != null && id.equals(GUIUtil.SHOW_ALL_FLAG);
    }

    private boolean isEditEntry(@Nullable String id) {
        return id != null && id.equals(GUIUtil.EDIT_FLAG);
    }
}
