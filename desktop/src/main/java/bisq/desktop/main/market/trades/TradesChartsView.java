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

import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.AutoTooltipToggleButton;
import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.ColoredDecimalPlacesWithZerosText;
import bisq.desktop.main.market.trades.charts.price.CandleStickChart;
import bisq.desktop.main.market.trades.charts.volume.VolumeChart;
import bisq.desktop.util.CurrencyListItem;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.user.CookieKey;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.UserThread;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import com.googlecode.jcsv.writer.CSVEntryConverter;

import javax.inject.Inject;
import javax.inject.Named;

import com.jfoenix.controls.JFXTabPane;

import javafx.stage.Stage;

import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.text.DecimalFormat;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.addTopLabelAutocompleteComboBox;
import static bisq.desktop.util.FormBuilder.getTopLabelWithVBox;

@FxmlView
public class TradesChartsView extends ActivatableViewAndModel<VBox, TradesChartsViewModel> {
    private static final int SHOW_ALL = 0;

    static class CurrencyStringConverter extends StringConverter<CurrencyListItem> {
        private final ComboBox<CurrencyListItem> comboBox;

        CurrencyStringConverter(ComboBox<CurrencyListItem> comboBox) {
            this.comboBox = comboBox;
        }

        @Override
        public String toString(CurrencyListItem currencyItem) {
            return currencyItem != null ? currencyItem.codeDashNameString() : "";
        }

        @Override
        public CurrencyListItem fromString(String query) {
            if (comboBox.getItems().isEmpty())
                return null;
            if (query.isEmpty())
                return specialShowAllItem();
            return comboBox.getItems().stream().
                    filter(currencyItem -> currencyItem.codeDashNameString().equals(query)).
                    findAny().orElse(null);
        }

        private CurrencyListItem specialShowAllItem() {
            return comboBox.getItems().get(0);
        }
    }

    private final User user;
    private final CoinFormatter coinFormatter;

    private VolumeChart volumeChart, volumeInUsdChart;
    private CandleStickChart priceChart;
    private AutocompleteComboBox<CurrencyListItem> currencyComboBox;
    private TableView<TradeStatistics3ListItem> tableView;
    private Hyperlink exportLink;
    private HBox toolBox;
    private Pane rootParent;
    private AnchorPane priceChartPane, volumeChartPane;
    private HBox footer;
    private AutoTooltipSlideToggleButton showVolumeAsUsdToggleButton;
    private Label nrOfTradeStatisticsLabel;
    private ToggleGroup toggleGroup;
    private SingleSelectionModel<Tab> tabPaneSelectionModel;

    private TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> priceColumn, volumeColumn, marketColumn;
    private final ObservableList<TradeStatistics3ListItem> listItems = FXCollections.observableArrayList();
    private final SortedList<TradeStatistics3ListItem> sortedList = new SortedList<>(listItems);

    private ChangeListener<Toggle> timeUnitChangeListener;
    private ChangeListener<Number> priceAxisYWidthListener;
    private ChangeListener<Number> volumeAxisYWidthListener;
    private ChangeListener<Number> selectedTabIndexListener;
    private ChangeListener<Number> parentHeightListener;
    private ChangeListener<String> priceColumnLabelListener;
    private ListChangeListener<XYChart.Data<Number, Number>> itemsChangeListener;
    private ListChangeListener<TradeStatistics3> tradeStatisticsByCurrencyListener;

    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Void> currencySelectionBinding;
    private Subscription currencySelectionSubscriber;

    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private NumberAxis priceAxisX, priceAxisY, volumeAxisY, volumeAxisX, volumeInUsdAxisX;
    private XYChart.Series<Number, Number> priceSeries;
    private final XYChart.Series<Number, Number> volumeSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> volumeInUsdSeries = new XYChart.Series<>();
    private double priceAxisYWidth;
    private double volumeAxisYWidth;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public TradesChartsView(TradesChartsViewModel model,
                            User user,
                            @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter coinFormatter) {
        super(model);
        this.user = user;
        this.coinFormatter = coinFormatter;
    }

    @Override
    public void initialize() {
        root.setAlignment(Pos.CENTER_LEFT);
        toolBox = getToolBox();

        createCharts();

        createTable();

        footer = new HBox();
        VBox.setVgrow(footer, Priority.ALWAYS);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        nrOfTradeStatisticsLabel = new AutoTooltipLabel(" "); // set empty string for layout
        nrOfTradeStatisticsLabel.setPadding(new Insets(-2, 0, -10, 12));

        exportLink = new Hyperlink(Res.get("shared.exportCSV"));
        exportLink.setPadding(new Insets(-2, 12, -10, 0));

        footer.getChildren().addAll(nrOfTradeStatisticsLabel, spacer, exportLink);

        root.getChildren().addAll(toolBox, priceChartPane, volumeChartPane, tableView, footer);

        timeUnitChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                model.setTickUnit((TradesChartsViewModel.TickUnit) newValue.getUserData());
                priceAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
                volumeAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
                volumeInUsdAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
            }
        };
        priceAxisYWidthListener = (observable, oldValue, newValue) -> {
            priceAxisYWidth = (double) newValue;
            layoutChart();
        };
        volumeAxisYWidthListener = (observable, oldValue, newValue) -> {
            volumeAxisYWidth = (double) newValue;
            layoutChart();
        };
        tradeStatisticsByCurrencyListener = c -> {
            nrOfTradeStatisticsLabel.setText(Res.get("market.trades.nrOfTrades", model.selectedTradeStatistics.size()));
            fillList();
        };
        parentHeightListener = (observable, oldValue, newValue) -> layout();

        priceColumnLabelListener = (o, oldVal, newVal) -> priceColumn.setGraphic(new AutoTooltipLabel(newVal));

        // Need to render on next frame as otherwise there are issues in the chart rendering
        itemsChangeListener = c -> UserThread.execute(this::updateChartData);

        currencySelectionBinding = EasyBind.combine(
                model.showAllTradeCurrenciesProperty, model.selectedTradeCurrencyProperty,
                (showAll, selectedTradeCurrency) -> {
                    priceChart.setVisible(!showAll);
                    priceChart.setManaged(!showAll);
                    priceColumn.setSortable(!showAll);

                    if (showAll) {
                        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amount")));
                        priceColumnLabel.set(Res.get("shared.price"));
                        if (!tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().add(1, marketColumn);

                        volumeChart.setPrefHeight(volumeChart.getMaxHeight());
                        volumeInUsdChart.setPrefHeight(volumeInUsdChart.getMaxHeight());
                    } else {
                        volumeChart.setPrefHeight(volumeChart.getMinHeight());
                        volumeInUsdChart.setPrefHeight(volumeInUsdChart.getMinHeight());
                        priceSeries.setName(selectedTradeCurrency.getName());
                        String code = selectedTradeCurrency.getCode();
                        volumeColumn.setGraphic(new AutoTooltipLabel(Res.get("shared.amountWithCur", code)));

                        priceColumnLabel.set(CurrencyUtil.getPriceWithCurrencyCode(code));

                        tableView.getColumns().remove(marketColumn);
                    }

                    layout();
                    return null;
                });

    }

    @Override
    protected void activate() {
        // root.getParent() is null at initialize
        tabPaneSelectionModel = GUIUtil.getParentOfType(root, JFXTabPane.class).getSelectionModel();
        selectedTabIndexListener = (observable, oldValue, newValue) -> model.setSelectedTabIndex((int) newValue);
        model.setSelectedTabIndex(tabPaneSelectionModel.getSelectedIndex());
        tabPaneSelectionModel.selectedIndexProperty().addListener(selectedTabIndexListener);

        currencyComboBox.setConverter(new CurrencyStringConverter(currencyComboBox));
        currencyComboBox.getEditor().getStyleClass().add("combo-box-editor-bold");

        currencyComboBox.setAutocompleteItems(model.getCurrencyListItems());
        currencyComboBox.setVisibleRowCount(10);

        if (model.showAllTradeCurrenciesProperty.get())
            currencyComboBox.getSelectionModel().select(SHOW_ALL);
        else if (model.getSelectedCurrencyListItem().isPresent())
            currencyComboBox.getSelectionModel().select(model.getSelectedCurrencyListItem().get());
        currencyComboBox.getEditor().setText(new CurrencyStringConverter(currencyComboBox).toString(currencyComboBox.getSelectionModel().getSelectedItem()));

        currencyComboBox.setOnChangeConfirmed(e -> {
            if (currencyComboBox.getEditor().getText().isEmpty())
                currencyComboBox.getSelectionModel().select(SHOW_ALL);
            CurrencyListItem selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                model.onSetTradeCurrency(selectedItem.tradeCurrency);
            }
        });

        toggleGroup.getToggles().get(model.tickUnit.ordinal()).setSelected(true);

        model.priceItems.addListener(itemsChangeListener);
        toggleGroup.selectedToggleProperty().addListener(timeUnitChangeListener);
        priceAxisY.widthProperty().addListener(priceAxisYWidthListener);
        volumeAxisY.widthProperty().addListener(volumeAxisYWidthListener);
        model.selectedTradeStatistics.addListener(tradeStatisticsByCurrencyListener);

        priceAxisY.labelProperty().bind(priceColumnLabel);
        priceColumnLabel.addListener(priceColumnLabelListener);
        currencySelectionSubscriber = currencySelectionBinding.subscribe((observable, oldValue, newValue) -> {
        });

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        boolean useAnimations = model.preferences.isUseAnimations();
        priceChart.setAnimated(useAnimations);
        volumeChart.setAnimated(useAnimations);
        volumeInUsdChart.setAnimated(useAnimations);
        priceAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
        volumeAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
        volumeInUsdAxisX.setTickLabelFormatter(getTimeAxisStringConverter());

        nrOfTradeStatisticsLabel.setText(Res.get("market.trades.nrOfTrades", model.selectedTradeStatistics.size()));

        exportLink.setOnAction(e -> exportToCsv());
        UserThread.runAfter(this::updateChartData, 100, TimeUnit.MILLISECONDS);

        if (root.getParent() instanceof Pane) {
            rootParent = (Pane) root.getParent();
            rootParent.heightProperty().addListener(parentHeightListener);
        }

        user.getCookie().getAsOptionalBoolean(CookieKey.TRADE_STAT_CHART_USE_USD).ifPresent(showUsd -> {
            showVolumeAsUsdToggleButton.setSelected(showUsd);
            showVolumeAsUsd(showUsd);
        });
        showVolumeAsUsdToggleButton.setOnAction(e -> {
            boolean selected = showVolumeAsUsdToggleButton.isSelected();
            showVolumeAsUsd(selected);
            user.getCookie().putAsBoolean(CookieKey.TRADE_STAT_CHART_USE_USD, selected);
            user.requestPersistence();
        });

        fillList();
        tableView.setItems(sortedList);
        layout();
    }

    @Override
    protected void deactivate() {
        tabPaneSelectionModel.selectedIndexProperty().removeListener(selectedTabIndexListener);
        model.priceItems.removeListener(itemsChangeListener);
        toggleGroup.selectedToggleProperty().removeListener(timeUnitChangeListener);
        priceAxisY.widthProperty().removeListener(priceAxisYWidthListener);
        volumeAxisY.widthProperty().removeListener(volumeAxisYWidthListener);
        model.selectedTradeStatistics.removeListener(tradeStatisticsByCurrencyListener);

        priceAxisY.labelProperty().unbind();
        priceColumnLabel.removeListener(priceColumnLabelListener);

        currencySelectionSubscriber.unsubscribe();

        sortedList.comparatorProperty().unbind();

        priceSeries.getData().clear();
        priceChart.getData().clear();

        exportLink.setOnAction(null);
        showVolumeAsUsdToggleButton.setOnAction(null);

        if (rootParent != null) {
            rootParent.heightProperty().removeListener(parentHeightListener);
        }
    }

    private void showVolumeAsUsd(Boolean showUsd) {
        volumeChart.setVisible(!showUsd);
        volumeChart.setManaged(!showUsd);
        volumeInUsdChart.setVisible(showUsd);
        volumeInUsdChart.setManaged(showUsd);
    }

    private void fillList() {
        List<TradeStatistics3ListItem> tradeStatistics3ListItems = model.selectedTradeStatistics.stream()
                .map(tradeStatistics -> new TradeStatistics3ListItem(tradeStatistics,
                        coinFormatter,
                        model.showAllTradeCurrenciesProperty.get()))
                .collect(Collectors.toList());
        listItems.setAll(tradeStatistics3ListItems);
    }

    private void exportToCsv() {
        ObservableList<TableColumn<TradeStatistics3ListItem, ?>> tableColumns = tableView.getColumns();
        int reportColumns = tableColumns.size() + 1;

        boolean showAllTradeCurrencies = model.showAllTradeCurrenciesProperty.get();
        CSVEntryConverter<TradeStatistics3ListItem> headerConverter = item -> {
            String[] columns = new String[reportColumns];
            columns[0] = "Epoch time in ms";
            for (int i = 0; i < tableColumns.size(); i++) {
                columns[(i + 1)] = ((AutoTooltipLabel) tableColumns.get(i).getGraphic()).getText();
            }
            return columns;
        };

        CSVEntryConverter<TradeStatistics3ListItem> contentConverter;
        if (showAllTradeCurrencies) {
            contentConverter = item -> {
                String[] columns = new String[reportColumns];
                columns[0] = String.valueOf(item.getDateAsLong());
                columns[1] = item.getDateString();
                columns[2] = item.getMarket();
                columns[3] = item.getPriceString();
                columns[4] = item.getAmountString();
                columns[5] = item.getVolumeString();
                columns[6] = item.getPaymentMethodString();
                return columns;
            };
        } else {
            contentConverter = item -> {
                String[] columns = new String[reportColumns];
                columns[0] = String.valueOf(item.getDateAsLong());
                columns[1] = item.getDateString();
                columns[2] = item.getPriceString();
                columns[3] = item.getAmountString();
                columns[4] = item.getVolumeString();
                columns[5] = item.getPaymentMethodString();
                return columns;
            };
        }

        String details = showAllTradeCurrencies ? "all-markets" : model.getCurrencyCode();
        GUIUtil.exportCSV("trade-statistics-" + details + ".csv", headerConverter, contentConverter,
                new TradeStatistics3ListItem(null, coinFormatter, showAllTradeCurrencies),
                sortedList,
                (Stage) root.getScene().getWindow());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createCharts() {
        priceSeries = new XYChart.Series<>();

        priceAxisX = new NumberAxis(0, model.maxTicks + 1, 1);
        priceAxisX.setTickUnit(4);
        priceAxisX.setMinorTickCount(4);
        priceAxisX.setMinorTickVisible(true);
        priceAxisX.setForceZeroInRange(false);
        priceAxisX.setTickLabelFormatter(getTimeAxisStringConverter());
        addTickMarkLabelCssClass(priceAxisX, "axis-tick-mark-text-node");

        priceAxisY = new NumberAxis();
        priceAxisY.setForceZeroInRange(false);
        priceAxisY.setAutoRanging(true);
        priceAxisY.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                String currencyCode = model.getCurrencyCode();
                double doubleValue = (double) object;

                if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                    final double value = MathUtils.scaleDownByPowerOf10(doubleValue, 8);
                    return FormattingUtils.formatRoundedDoubleWithPrecision(value, 8).replaceFirst("0{3}$", "");
                } else {
                    DecimalFormat df = new DecimalFormat(",###");
                    return df.format(Double.parseDouble(FormattingUtils.formatPrice(Price.valueOf(currencyCode, MathUtils.doubleToLong(doubleValue)))));
                }
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        priceChart = new CandleStickChart(priceAxisX, priceAxisY, new StringConverter<>() {
            @Override
            public String toString(Number object) {
                if (CurrencyUtil.isCryptoCurrency(model.getCurrencyCode())) {
                    final double value = MathUtils.scaleDownByPowerOf10((long) object, 8);
                    return FormattingUtils.formatRoundedDoubleWithPrecision(value, 8);
                } else {
                    return FormattingUtils.formatPrice(Price.valueOf(model.getCurrencyCode(), (long) object));
                }
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });
        priceChart.setId("price-chart");
        priceChart.setMinHeight(188);
        priceChart.setPrefHeight(188);
        priceChart.setMaxHeight(300);
        priceChart.setLegendVisible(false);
        priceChart.setPadding(new Insets(0));
        priceChart.setData(FXCollections.observableArrayList(List.of(priceSeries)));

        priceChartPane = new AnchorPane();
        priceChartPane.getStyleClass().add("chart-pane");

        AnchorPane.setTopAnchor(priceChart, 15d);
        AnchorPane.setBottomAnchor(priceChart, 10d);
        AnchorPane.setLeftAnchor(priceChart, 0d);
        AnchorPane.setRightAnchor(priceChart, 10d);

        priceChartPane.getChildren().add(priceChart);

        volumeAxisX = new NumberAxis(0, model.maxTicks + 1, 1);
        volumeAxisY = new NumberAxis();
        volumeChart = getVolumeChart(volumeAxisX, volumeAxisY, volumeSeries, "BTC");

        volumeInUsdAxisX = new NumberAxis(0, model.maxTicks + 1, 1);
        NumberAxis volumeInUsdAxisY = new NumberAxis();
        volumeInUsdChart = getVolumeChart(volumeInUsdAxisX, volumeInUsdAxisY, volumeInUsdSeries, "USD");
        volumeInUsdChart.setVisible(false);
        volumeInUsdChart.setManaged(false);

        showVolumeAsUsdToggleButton = new AutoTooltipSlideToggleButton();
        showVolumeAsUsdToggleButton.setText(Res.get("market.trades.showVolumeInUSD"));
        showVolumeAsUsdToggleButton.setPadding(new Insets(-15, 0, 0, 10));

        VBox vBox = new VBox();
        AnchorPane.setTopAnchor(vBox, 15d);
        AnchorPane.setBottomAnchor(vBox, 10d);
        AnchorPane.setLeftAnchor(vBox, 0d);
        AnchorPane.setRightAnchor(vBox, 10d);
        vBox.getChildren().addAll(showVolumeAsUsdToggleButton, volumeChart, volumeInUsdChart);

        volumeChartPane = new AnchorPane();
        volumeChartPane.getStyleClass().add("chart-pane");
        volumeChartPane.getChildren().add(vBox);
    }

    private VolumeChart getVolumeChart(NumberAxis axisX,
                                       NumberAxis axisY,
                                       XYChart.Series<Number, Number> series,
                                       String currency) {
        axisX.setTickUnit(4);
        axisX.setMinorTickCount(4);
        axisX.setMinorTickVisible(true);
        axisX.setForceZeroInRange(false);
        axisX.setTickLabelFormatter(getTimeAxisStringConverter());
        addTickMarkLabelCssClass(axisX, "axis-tick-mark-text-node");

        axisY.setForceZeroInRange(true);
        axisY.setAutoRanging(true);
        axisY.setLabel(Res.get("shared.volumeWithCur", currency));
        axisY.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number volume) {
                return currency.equals("BTC") ?
                        coinFormatter.formatCoin(Coin.valueOf(MathUtils.doubleToLong((double) volume))) :
                        DisplayUtils.formatLargeFiatWithUnitPostFix((double) volume, "USD");
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        StringConverter<Number> btcStringConverter = new StringConverter<>() {
            @Override
            public String toString(Number volume) {
                return coinFormatter.formatCoinWithCode(Coin.valueOf((long) volume));
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
        VolumeChart volumeChart = new VolumeChart(axisX, axisY, btcStringConverter);
        volumeChart.setId("volume-chart");
        volumeChart.setData(FXCollections.observableArrayList(List.of(series)));
        volumeChart.setMinHeight(138);
        volumeChart.setPrefHeight(138);
        volumeChart.setMaxHeight(200);
        volumeChart.setLegendVisible(false);
        volumeChart.setPadding(new Insets(0));
        return volumeChart;
    }

    private void updateChartData() {
        volumeSeries.getData().setAll(model.volumeItems);
        volumeInUsdSeries.getData().setAll(model.volumeInUsdItems);

        // At price chart we need to set the priceSeries new otherwise the lines are not rendered correctly
        // TODO should be fixed in candle chart
        priceSeries.getData().clear();
        priceSeries = new XYChart.Series<>();
        priceSeries.getData().setAll(model.priceItems);
        priceChart.getData().clear();
        priceChart.setData(FXCollections.observableArrayList(List.of(priceSeries)));
    }

    private void layoutChart() {
        UserThread.execute(() -> {
            if (volumeAxisYWidth > priceAxisYWidth) {
                priceChart.setPadding(new Insets(0, 0, 0, volumeAxisYWidth - priceAxisYWidth));
                volumeChart.setPadding(new Insets(0, 0, 0, 0));
                volumeInUsdChart.setPadding(new Insets(0, 0, 0, 0));
            } else if (volumeAxisYWidth < priceAxisYWidth) {
                priceChart.setPadding(new Insets(0, 0, 0, 0));
                volumeChart.setPadding(new Insets(0, 0, 0, priceAxisYWidth - volumeAxisYWidth));
                volumeInUsdChart.setPadding(new Insets(0, 0, 0, priceAxisYWidth - volumeAxisYWidth));
            }
        });
    }

    @NotNull
    private StringConverter<Number> getTimeAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number object) {
                long index = MathUtils.doubleToLong((double) object);
                // The last tick is on the chart edge, it is not well spaced with
                // the previous tick and interferes with its label.
                if (model.maxTicks + 1 == index) return "";

                long time = model.getTimeFromTickIndex(index);
                String fmt = "";
                switch (model.tickUnit) {
                    case YEAR:
                        fmt = "yyyy";
                        break;
                    case MONTH:
                        fmt = "MMMyy";
                        break;
                    case WEEK:
                    case DAY:
                        fmt = "dd/MMM\nyyyy";
                        break;
                    case HOUR:
                    case MINUTE_10:
                        fmt = "HH:mm\ndd/MMM";
                        break;
                    default:        // nothing here
                }

                return DisplayUtils.formatDateAxis(new Date(time), fmt);
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    private void addTickMarkLabelCssClass(NumberAxis axis, String cssClass) {
        // grab the axis tick mark label (text object) and add a CSS class.
        axis.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Node mark : c.getAddedSubList()) {
                        if (mark instanceof Text) {
                            mark.getStyleClass().add(cssClass);
                        }
                    }
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // CurrencyComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    private HBox getToolBox() {

        final Tuple3<VBox, Label, AutocompleteComboBox<CurrencyListItem>> currencyComboBoxTuple = addTopLabelAutocompleteComboBox(
                Res.get("shared.currency"));
        currencyComboBox = currencyComboBoxTuple.third;
        currencyComboBox.setCellFactory(GUIUtil.getCurrencyListItemCellFactory(Res.get("shared.trade"),
                Res.get("shared.trades"), model.preferences));

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toggleGroup = new ToggleGroup();
        ToggleButton year = getToggleButton(Res.get("time.year"), TradesChartsViewModel.TickUnit.YEAR, toggleGroup, "toggle-left");
        ToggleButton month = getToggleButton(Res.get("time.month"), TradesChartsViewModel.TickUnit.MONTH, toggleGroup, "toggle-center");
        ToggleButton week = getToggleButton(Res.get("time.week"), TradesChartsViewModel.TickUnit.WEEK, toggleGroup, "toggle-center");
        ToggleButton day = getToggleButton(Res.get("time.day"), TradesChartsViewModel.TickUnit.DAY, toggleGroup, "toggle-center");
        ToggleButton hour = getToggleButton(Res.get("time.hour"), TradesChartsViewModel.TickUnit.HOUR, toggleGroup, "toggle-center");
        ToggleButton minute10 = getToggleButton(Res.get("time.minute10"), TradesChartsViewModel.TickUnit.MINUTE_10, toggleGroup, "toggle-right");

        HBox toggleBox = new HBox();
        toggleBox.setSpacing(0);
        toggleBox.setAlignment(Pos.CENTER_LEFT);
        toggleBox.getChildren().addAll(year, month, week, day, hour, minute10);

        final Tuple2<Label, VBox> topLabelWithVBox = getTopLabelWithVBox(Res.get("shared.interval"), toggleBox);

        HBox hBox = new HBox();
        hBox.setSpacing(0);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(currencyComboBoxTuple.first, spacer, topLabelWithVBox.second);
        return hBox;
    }

    private ToggleButton getToggleButton(String label,
                                         TradesChartsViewModel.TickUnit tickUnit,
                                         ToggleGroup toggleGroup,
                                         String style) {
        ToggleButton toggleButton = new AutoTooltipToggleButton(label);
        toggleButton.setUserData(tickUnit);
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId(style);
        return toggleButton;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createTable() {
        tableView = new TableView<>();
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // date
        TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> dateColumn = new AutoTooltipTableColumn<>(Res.get("shared.dateTime")) {
            {
                setMinWidth(240);
                setMaxWidth(240);
            }
        };
        dateColumn.getStyleClass().addAll("number-column", "first-column");
        dateColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        dateColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics3ListItem, TradeStatistics3ListItem> call(
                            TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics3ListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null) {
                                    setText(item.getDateString());
                                } else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator(Comparator.comparing(TradeStatistics3ListItem::getDate));
        tableView.getColumns().add(dateColumn);

        // market
        marketColumn = new AutoTooltipTableColumn<>(Res.get("shared.market")) {
            {
                setMinWidth(130);
                setMaxWidth(130);
            }
        };
        marketColumn.getStyleClass().add("number-column");
        marketColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        marketColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics3ListItem, TradeStatistics3ListItem> call(
                            TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics3ListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMarket());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        marketColumn.setComparator(Comparator.comparing(TradeStatistics3ListItem::getMarket));
        tableView.getColumns().add(marketColumn);

        // price
        priceColumn = new TableColumn<>();
        priceColumn.getStyleClass().add("number-column");
        priceColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        priceColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics3ListItem, TradeStatistics3ListItem> call(
                            TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics3ListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getPriceString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        priceColumn.setComparator(Comparator.comparing(TradeStatistics3ListItem::getTradePrice));
        tableView.getColumns().add(priceColumn);

        // amount
        TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> amountColumn = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", Res.getBaseCurrencyCode()));
        amountColumn.getStyleClass().add("number-column");
        amountColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        amountColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics3ListItem, TradeStatistics3ListItem> call(
                            TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics3ListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null) {
                                    setGraphic(new ColoredDecimalPlacesWithZerosText(item.getAmountString(),
                                            GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                } else
                                    setGraphic(null);
                            }
                        };
                    }
                });
        amountColumn.setComparator(Comparator.comparing(TradeStatistics3ListItem::getTradeAmount));
        tableView.getColumns().add(amountColumn);

        // volume
        volumeColumn = new TableColumn<>();
        volumeColumn.getStyleClass().add("number-column");
        volumeColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        volumeColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics3ListItem, TradeStatistics3ListItem> call(
                            TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics3ListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getVolumeString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        volumeColumn.setComparator(Comparator.comparing(TradeStatistics3ListItem::getTradeVolume));
        tableView.getColumns().add(volumeColumn);

        // paymentMethod
        TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> paymentMethodColumn = new AutoTooltipTableColumn<>(Res.get("shared.paymentMethod"));
        paymentMethodColumn.getStyleClass().add("number-column");
        paymentMethodColumn.setCellValueFactory((tradeStatistics) -> new ReadOnlyObjectWrapper<>(tradeStatistics.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<TradeStatistics3ListItem, TradeStatistics3ListItem> call(
                            TableColumn<TradeStatistics3ListItem, TradeStatistics3ListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final TradeStatistics3ListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getPaymentMethodString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        paymentMethodColumn.setComparator(Comparator.comparing(TradeStatistics3ListItem::getPaymentMethodString));
        tableView.getColumns().add(paymentMethodColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noData"));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }

    private void layout() {
        double available;
        if (root.getParent() instanceof Pane) {
            available = ((Pane) root.getParent()).getHeight();
        } else {
            available = root.getHeight();
        }
        if (available == 0) {
            UserThread.execute(this::layout);
            return;
        }

        available = available - volumeChartPane.getHeight() - toolBox.getHeight() - footer.getHeight() - 60;

        if (!model.showAllTradeCurrenciesProperty.get()) {
            double priceChartPaneHeight = priceChartPane.getHeight();
            if (priceChartPaneHeight == 0) {
                UserThread.execute(this::layout);
                return;
            }
            available -= priceChartPaneHeight;
        } else {
            // If rendering is not done we get the height which is smaller than the volumeChart max Height so we
            // delay to next render frame.
            // Using runAfter does not work well as filling the table list and creating the chart can be a bit slow and
            // its hard to estimate correct delay.
            if (volumeChartPane.getHeight() < volumeChart.getMaxHeight()) {
                UserThread.execute(this::layout);
                return;
            }
        }
        tableView.setPrefHeight(available);
    }
}
