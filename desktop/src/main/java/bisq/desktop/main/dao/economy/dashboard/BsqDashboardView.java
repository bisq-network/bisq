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
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addLabelWithSubText;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;



import java.sql.Date;

@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private static final String DAY = "day";
    private static final Map<String, TemporalAdjuster> ADJUSTERS = new HashMap<>();

    private final DaoFacade daoFacade;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final PriceFeedService priceFeedService;
    private final DaoStateService daoStateService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;
    private final BSFormatter btcFormatter;

    private ChangeListener<Number> priceChangeListener;

    private AreaChart bsqPriceChart;
    private XYChart.Series<Number, Number> seriesBSQAdded, seriesBSQBurnt;
    private XYChart.Series<Number, Number> seriesBSQPrice;

    private TextField marketCapTextField, availableAmountTextField;
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
                             DaoStateService daoStateService,
                             Preferences preferences,
                             BsqFormatter bsqFormatter,
                             BSFormatter btcFormatter) {
        this.daoFacade = daoFacade;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.priceFeedService = priceFeedService;
        this.daoStateService = daoStateService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
    }

    @Override
    public void initialize() {

        ADJUSTERS.put(DAY, TemporalAdjusters.ofDateAdjuster(d -> d));

        createKPIs();
        createChart();

        priceChangeListener = (observable, oldValue, newValue) -> updatePrice();
    }

    private void createKPIs() {

        Tuple3<Label, Label, VBox> marketPriceBox = addLabelWithSubText(root, gridRow++, "0.004000 BSQ/BTC", "Latest BSQ/BTC trade price (in Bisq)");
        marketPriceLabel = marketPriceBox.first;
        marketPriceLabel.getStyleClass().add("dao-kpi-big");

        marketPriceBox.second.getStyleClass().add("dao-kpi-subtext");

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
        bsqPriceChart.setMinHeight(385);
        bsqPriceChart.setPrefHeight(385);
        bsqPriceChart.setCreateSymbols(true);
        bsqPriceChart.setPadding(new Insets(0));
        bsqPriceChart.getData().addAll(seriesBSQPrice);

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
        updateBSQPriceData();
    }

    private void updateBSQPriceData() {
        seriesBSQPrice.getData().clear();

        Map<LocalDate, List<TradeStatistics2>> bsqPriceByDate = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> e.getCurrencyCode().equals("BSQ"))
                .sorted(Comparator.comparing(TradeStatistics2::getTradeDate))
                .collect(Collectors.groupingBy(item -> new Date(item.getTradeDate().getTime()).toLocalDate()
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
            marketPriceLabel.setText(bsqFormatter.formatPrice(bsqPrice) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(priceFeedService.getMarketPrice("BSQ"),
                    priceFeedService.getMarketPrice(preferences.getPreferredTradeCurrency().getCode()),
                    availableAmount));

            updateChartData();

        } else {
            marketPriceLabel.setText(Res.get("shared.na"));
            marketCapTextField.setText(Res.get("shared.na"));
        }
    }
}

