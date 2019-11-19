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

package bisq.desktop.main.dao.economy.dashboard;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.TextFieldWithIcon;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.FormattingUtils;

import bisq.common.util.MathUtils;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Side;

import javafx.beans.value.ChangeListener;

import javafx.collections.ObservableList;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addLabelWithSubText;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelTextFieldWithIcon;


@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private static final String DAY = "day";
    private static final Map<String, TemporalAdjuster> ADJUSTERS = new HashMap<>();

    private final DaoFacade daoFacade;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;

    private ChangeListener<Number> priceChangeListener;

    private AreaChart<Number, Number> bsqPriceChart;
    private XYChart.Series<Number, Number> seriesBSQPrice;

    private TextField avgPrice90TextField, marketCapTextField, availableAmountTextField;
    private TextFieldWithIcon avgPrice30TextField;
    private Label marketPriceLabel;

    private Coin availableAmount;

    private int gridRow = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqDashboardView(DaoFacade daoFacade,
                             TradeStatisticsManager tradeStatisticsManager,
                             PriceFeedService priceFeedService,
                             Preferences preferences,
                             BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {

        ADJUSTERS.put(DAY, TemporalAdjusters.ofDateAdjuster(d -> d));

        createKPIs();
        createChart();

        priceChangeListener = (observable, oldValue, newValue) -> {
            updatePrice();
            updateAveragePriceFields();
        };
    }

    private void createKPIs() {

        Tuple3<Label, Label, VBox> marketPriceBox = addLabelWithSubText(root, gridRow++, "", "");
        marketPriceLabel = marketPriceBox.first;
        marketPriceLabel.getStyleClass().add("dao-kpi-big");

        marketPriceBox.second.getStyleClass().add("dao-kpi-subtext");

        avgPrice90TextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.dashboard.avgPrice90")).second;

        avgPrice30TextField = addTopLabelTextFieldWithIcon(root, gridRow, 1,
                Res.get("dao.factsAndFigures.dashboard.avgPrice30"), -15).second;
        AnchorPane.setRightAnchor(avgPrice30TextField.getIconLabel(), 10d);

        marketCapTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.dashboard.marketCap")).second;

        availableAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.dashboard.availableAmount")).second;
    }


    @Override
    protected void activate() {
        daoFacade.addBsqStateListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        updateWithBsqBlockChainData();
        updatePrice();
        updateChartData();
        updateAveragePriceFields();
    }


    @Override
    protected void deactivate() {
        daoFacade.removeBsqStateListener(this);
        priceFeedService.updateCounterProperty().removeListener(priceChangeListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateWithBsqBlockChainData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(true);
        xAxis.setTickLabelGap(6);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);

        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number timestamp) {
                LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(timestamp.longValue(),
                        0, OffsetDateTime.now(ZoneId.systemDefault()).getOffset());
                return localDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setSide(Side.RIGHT);
        yAxis.setAutoRanging(true);
        yAxis.setTickMarkVisible(false);
        yAxis.setMinorTickVisible(false);
        yAxis.setTickLabelGap(5);
        yAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number marketPrice) {
                return bsqFormatter.formatBTCWithCode(marketPrice.longValue());
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        seriesBSQPrice = new XYChart.Series<>();
        seriesBSQPrice.setName("Price in BTC for 1 BSQ");

        bsqPriceChart = new AreaChart<>(xAxis, yAxis);
        bsqPriceChart.setLegendVisible(false);
        bsqPriceChart.setAnimated(false);
        bsqPriceChart.setId("charts-dao");
        bsqPriceChart.setMinHeight(320);
        bsqPriceChart.setPrefHeight(bsqPriceChart.getMinHeight());
        bsqPriceChart.setCreateSymbols(true);
        bsqPriceChart.setPadding(new Insets(0));
        bsqPriceChart.getData().add(seriesBSQPrice);

        AnchorPane chartPane = new AnchorPane();
        chartPane.getStyleClass().add("chart-pane");

        AnchorPane.setTopAnchor(bsqPriceChart, 15d);
        AnchorPane.setBottomAnchor(bsqPriceChart, 10d);
        AnchorPane.setLeftAnchor(bsqPriceChart, 25d);
        AnchorPane.setRightAnchor(bsqPriceChart, 10d);

        chartPane.getChildren().add(bsqPriceChart);

        GridPane.setRowIndex(chartPane, ++gridRow);
        GridPane.setColumnSpan(chartPane, 2);
        GridPane.setMargin(chartPane, new Insets(10, 0, 0, 0));

        root.getChildren().addAll(chartPane);
    }

    private void updateChartData() {
        updateBsqPriceData();
    }

    private void updateBsqPriceData() {
        seriesBSQPrice.getData().clear();

        Map<LocalDate, List<TradeStatistics2>> bsqPriceByDate = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> e.getCurrencyCode().equals("BSQ"))
                .sorted(Comparator.comparing(TradeStatistics2::getTradeDate))
                .collect(Collectors.groupingBy(item -> new java.sql.Date(item.getTradeDate().getTime()).toLocalDate()
                        .with(ADJUSTERS.get(DAY))));

        List<XYChart.Data<Number, Number>> updatedBSQPrice = bsqPriceByDate.keySet().stream()
                .map(e -> {
                    ZonedDateTime zonedDateTime = e.atStartOfDay(ZoneId.systemDefault());
                    return new XYChart.Data<Number, Number>(zonedDateTime.toInstant().getEpochSecond(), bsqPriceByDate.get(e).stream()
                            .map(TradeStatistics2::getTradePrice)
                            .mapToDouble(Price::getValue)
                            .average()
                            .orElse(Double.NaN)
                    );
                })
                .collect(Collectors.toList());

        seriesBSQPrice.getData().setAll(updatedBSQPrice);
    }

    private void updateWithBsqBlockChainData() {
        Coin issuedAmountFromGenesis = daoFacade.getGenesisTotalSupply();
        Coin issuedAmountFromCompRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.COMPENSATION));
        Coin issuedAmountFromReimbursementRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.REIMBURSEMENT));
        Coin totalConfiscatedAmount = Coin.valueOf(daoFacade.getTotalAmountOfConfiscatedTxOutputs());
        // Contains burnt fee and invalidated bsq due invalid txs
        Coin totalAmountOfBurntBsq = Coin.valueOf(daoFacade.getTotalAmountOfBurntBsq());

        availableAmount = issuedAmountFromGenesis
                .add(issuedAmountFromCompRequests)
                .add(issuedAmountFromReimbursementRequests)
                .subtract(totalAmountOfBurntBsq)
                .subtract(totalConfiscatedAmount);

        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
    }

    private void updatePrice() {
        Optional<Price> optionalBsqPrice = priceFeedService.getBsqPrice();
        if (optionalBsqPrice.isPresent()) {
            Price bsqPrice = optionalBsqPrice.get();
            marketPriceLabel.setText(FormattingUtils.formatPrice(bsqPrice) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(priceFeedService.getMarketPrice("BSQ"),
                    priceFeedService.getMarketPrice(preferences.getPreferredTradeCurrency().getCode()),
                    availableAmount));

            updateChartData();

        } else {
            marketPriceLabel.setText(Res.get("shared.na"));
            marketCapTextField.setText(Res.get("shared.na"));
        }
    }

    private void updateAveragePriceFields() {
        long average90 = updateAveragePriceField(avgPrice90TextField, 90);
        long average30 = updateAveragePriceField(avgPrice30TextField.getTextField(), 30);
        boolean trendUp = average30 > average90;
        boolean trendDown = average30 < average90;

        Label iconLabel = avgPrice30TextField.getIconLabel();
        ObservableList<String> styleClass = iconLabel.getStyleClass();
        if (trendUp) {
            avgPrice30TextField.setVisible(true);
            avgPrice30TextField.setIcon(AwesomeIcon.CIRCLE_ARROW_UP);
            styleClass.remove("price-trend-down");
            styleClass.add("price-trend-up");
        } else if (trendDown) {
            avgPrice30TextField.setVisible(true);
            avgPrice30TextField.setIcon(AwesomeIcon.CIRCLE_ARROW_DOWN);
            styleClass.remove("price-trend-up");
            styleClass.add("price-trend-down");
        } else {
            iconLabel.setVisible(false);
        }
    }

    private long updateAveragePriceField(TextField textField, int days) {
        Date past90 = getPastDate(days);
        List<TradeStatistics2> bsqTradePast90Days = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> e.getCurrencyCode().equals("BSQ"))
                .filter(e -> e.getTradeDate().after(past90))
                .collect(Collectors.toList());
        long average = getAverage(bsqTradePast90Days);
        Coin oneBsq = Coin.valueOf(100);
        Price avgPrice = Price.valueOf("BSQ", average);
        String avg = FormattingUtils.formatPrice(avgPrice);
        String bsqInUsdAvg = average > 0 ? GUIUtil.getBsqInUsd(avgPrice, oneBsq, priceFeedService, bsqFormatter) : Res.get("shared.na");
        textField.setText(avg + " BSQ/BTC (" + "1 BSQ = " + bsqInUsdAvg + ")");
        return average;
    }

    private long getAverage(List<TradeStatistics2> list) {
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;
        List<Long> tradePrices = new ArrayList<>(list.size());

        for (TradeStatistics2 item : list) {
            item.getTradeVolume();
            accumulatedVolume += item.getTradeVolume().getValue();
            accumulatedAmount += item.getTradeAmount().getValue();
            tradePrices.add(item.getTradePrice().getValue());
        }
        Collections.sort(tradePrices);
        list.sort(Comparator.comparingLong(o -> o.getTradeDate().getTime()));

        long averagePrice;
        Long[] prices = new Long[tradePrices.size()];
        tradePrices.toArray(prices);
        double accumulatedAmountAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedAmount, Altcoin.SMALLEST_UNIT_EXPONENT);
        averagePrice = accumulatedVolume > 0 ? MathUtils.roundDoubleToLong(accumulatedAmountAsDouble / (double) accumulatedVolume) : 0;

        return averagePrice;
    }

    private Date getPastDate(int days) {
        Calendar cal = new GregorianCalendar();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_MONTH, -1 * days);
        return cal.getTime();
    }
}

