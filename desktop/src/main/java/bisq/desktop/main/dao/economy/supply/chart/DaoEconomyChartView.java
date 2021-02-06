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

package bisq.desktop.main.dao.economy.supply.chart;

import bisq.desktop.components.chart.ChartView;
import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.UserThread;

import javax.inject.Inject;

import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.text.Text;

import javafx.geometry.Side;

import javafx.collections.ListChangeListener;

import javafx.util.StringConverter;

import java.time.Instant;

import java.text.DecimalFormat;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoEconomyChartView extends ChartView<DaoEconomyChartModel> {
    private static final DecimalFormat priceFormat = new DecimalFormat(",###");
    private final BsqFormatter bsqFormatter;

    private XYChart.Series<Number, Number> seriesBsqTradeFee, seriesProofOfBurn, seriesCompensation,
            seriesReimbursement, seriesTotalIssued, seriesTotalBurned;
    private ListChangeListener<Node> nodeListChangeListener;

    @Inject
    public DaoEconomyChartView(DaoEconomyChartModel model, BsqFormatter bsqFormatter) {
        super(model);

        this.bsqFormatter = bsqFormatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        super.initialize();

        // Turn off detail series
        hideSeries(seriesBsqTradeFee);
        hideSeries(seriesCompensation);
        hideSeries(seriesProofOfBurn);
        hideSeries(seriesReimbursement);

        nodeListChangeListener = c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Node mark : c.getAddedSubList()) {
                        if (mark instanceof Text) {
                            mark.getStyleClass().add("axis-tick-mark-text-node");
                        }
                    }
                }
            }
        };
    }

    @Override
    public void activate() {
        super.activate();
        xAxis.getChildrenUnmodifiable().addListener(nodeListChangeListener);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        xAxis.getChildrenUnmodifiable().removeListener(nodeListChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Customisations
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected NumberAxis getXAxis() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setTickLabelFormatter(getTimeAxisStringConverter());
        return xAxis;
    }

    @Override
    protected NumberAxis getYAxis() {
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setSide(Side.RIGHT);
        yAxis.setTickLabelFormatter(getYAxisStringConverter());
        return yAxis;
    }

    @Override
    protected XYChart<Number, Number> getChart() {
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.setId("charts-dao");
        return chart;
    }

    @Override
    protected StringConverter<Number> getTimeAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number epochSeconds) {
                Date date = new Date(model.toTimeInterval(Instant.ofEpochSecond(epochSeconds.longValue())) * 1000);
                return DisplayUtils.formatDateAxis(date, dateFormatPatters);
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        };
    }

    @Override
    protected StringConverter<Number> getYAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return priceFormat.format(Double.parseDouble(bsqFormatter.formatBSQSatoshis(value.longValue()))) + " BSQ";
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    @Override
    protected String getTooltipValueConverter(Number value) {
        return bsqFormatter.formatBSQSatoshisWithCode(value.longValue());
    }

    @Override
    protected void addSeries() {
        seriesTotalIssued = new XYChart.Series<>();
        seriesTotalIssued.setName(Res.get("dao.factsAndFigures.supply.totalIssued"));
        seriesIndexMap.put(seriesTotalIssued.getName(), 0);
        chart.getData().add(seriesTotalIssued);

        seriesTotalBurned = new XYChart.Series<>();
        seriesTotalBurned.setName(Res.get("dao.factsAndFigures.supply.totalBurned"));
        seriesIndexMap.put(seriesTotalBurned.getName(), 1);
        chart.getData().add(seriesTotalBurned);

        seriesCompensation = new XYChart.Series<>();
        seriesCompensation.setName(Res.get("dao.factsAndFigures.supply.compReq"));
        seriesIndexMap.put(seriesCompensation.getName(), 2);
        chart.getData().add(seriesCompensation);

        seriesReimbursement = new XYChart.Series<>();
        seriesReimbursement.setName(Res.get("dao.factsAndFigures.supply.reimbursement"));
        seriesIndexMap.put(seriesReimbursement.getName(), 3);
        chart.getData().add(seriesReimbursement);

        seriesBsqTradeFee = new XYChart.Series<>();
        seriesBsqTradeFee.setName(Res.get("dao.factsAndFigures.supply.bsqTradeFee"));
        seriesIndexMap.put(seriesBsqTradeFee.getName(), 4);
        chart.getData().add(seriesBsqTradeFee);

        seriesProofOfBurn = new XYChart.Series<>();
        seriesProofOfBurn.setName(Res.get("dao.factsAndFigures.supply.proofOfBurn"));
        seriesIndexMap.put(seriesProofOfBurn.getName(), 5);
        chart.getData().add(seriesProofOfBurn);
    }

    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend1() {
        return List.of(seriesTotalIssued, seriesCompensation, seriesReimbursement);
    }

    @Override
    protected Collection<XYChart.Series<Number, Number>> getSeriesForLegend2() {
        return List.of(seriesTotalBurned, seriesBsqTradeFee, seriesProofOfBurn);
    }

    @Override
    protected void initData() {
        Predicate<Long> predicate = e -> true;
        List<XYChart.Data<Number, Number>> bsqTradeFeeChartData = model.getBsqTradeFeeChartData(predicate);
        seriesBsqTradeFee.getData().setAll(bsqTradeFeeChartData);

        List<XYChart.Data<Number, Number>> compensationRequestsChartData = model.getCompensationChartData(predicate);
        seriesCompensation.getData().setAll(compensationRequestsChartData);

        // We don't need redundant data like reimbursementChartData as time value from compensationRequestsChartData
        // will cover it
        model.initBounds(bsqTradeFeeChartData, compensationRequestsChartData);
        xAxis.setLowerBound(model.getLowerBound().doubleValue());
        xAxis.setUpperBound(model.getUpperBound().doubleValue());

        updateOtherSeries(predicate);
        applyTooltip();

        UserThread.execute(this::setTimeLineLabels);
    }

    @Override
    protected void updateData(Predicate<Long> predicate) {
        seriesBsqTradeFee.getData().setAll(model.getBsqTradeFeeChartData(predicate));
        seriesCompensation.getData().setAll(model.getCompensationChartData(predicate));

        updateOtherSeries(predicate);

        applyTooltip();
        applySeriesStyles();
    }

    private void updateOtherSeries(Predicate<Long> predicate) {
        seriesProofOfBurn.getData().setAll(model.getProofOfBurnChartData(predicate));
        seriesReimbursement.getData().setAll(model.getReimbursementChartData(predicate));
        seriesTotalIssued.getData().setAll(model.getTotalIssuedChartData(predicate));
        seriesTotalBurned.getData().setAll(model.getTotalBurnedChartData(predicate));
    }
}
