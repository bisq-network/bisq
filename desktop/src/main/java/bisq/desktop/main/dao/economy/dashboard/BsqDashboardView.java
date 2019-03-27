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
import bisq.desktop.components.TitledGroupBg;
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

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;
import static bisq.desktop.util.Layout.FIRST_ROW_DISTANCE;



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

    private TextField marketCapTextField, priceTextField, availableAmountTextField;

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

        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 5, Res.get("dao.factsAndFigures.dashboard.marketPrice"));
        titledGroupBg.getStyleClass().add("last");

        Tuple3<Label, TextField, VBox> marketPriceTuple = addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.factsAndFigures.dashboard.price"),
                FIRST_ROW_DISTANCE);
        priceTextField = marketPriceTuple.second;

        GridPane.setColumnSpan(marketPriceTuple.third, 2);

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
        bsqPriceChart.setLegendVisible(true);
        bsqPriceChart.setAnimated(false);
        bsqPriceChart.setId("charts");
        bsqPriceChart.setMinHeight(250);
        bsqPriceChart.setPrefHeight(250);
        bsqPriceChart.setCreateSymbols(true);
        bsqPriceChart.setPadding(new Insets(0));
        bsqPriceChart.getData().addAll(seriesBSQPrice);

        GridPane.setRowIndex(bsqPriceChart, ++gridRow);
        GridPane.setColumnSpan(bsqPriceChart, 2);

        root.getChildren().addAll(bsqPriceChart);
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
        Coin burntFee = Coin.valueOf(daoFacade.getTotalBurntFee());
        Coin totalConfiscatedAmount = Coin.valueOf(daoFacade.getTotalAmountOfConfiscatedTxOutputs());

        availableAmount = issuedAmountFromGenesis
                .add(issuedAmountFromCompRequests)
                .add(issuedAmountFromReimbursementRequests)
                .subtract(burntFee)
                .subtract(totalConfiscatedAmount);

        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
    }

    private void updatePrice() {
        Optional<Price> optionalBsqPrice = priceFeedService.getBsqPrice();
        if (optionalBsqPrice.isPresent()) {
            Price bsqPrice = optionalBsqPrice.get();
            priceTextField.setText(bsqFormatter.formatPrice(bsqPrice) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(priceFeedService.getMarketPrice("BSQ"),
                    priceFeedService.getMarketPrice(preferences.getPreferredTradeCurrency().getCode()),
                    availableAmount));
        } else {
            priceTextField.setText(Res.get("shared.na"));
            marketCapTextField.setText(Res.get("shared.na"));
        }
    }
}

