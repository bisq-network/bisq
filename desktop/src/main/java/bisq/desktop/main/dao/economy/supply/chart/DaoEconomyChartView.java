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
    protected void initSeries() {
        seriesTotalIssued = new XYChart.Series<>();
        seriesTotalIssued.setName(Res.get("dao.factsAndFigures.supply.totalIssued"));
        seriesIndexMap.put(getSeriesId(seriesTotalIssued), 0);

        seriesTotalBurned = new XYChart.Series<>();
        seriesTotalBurned.setName(Res.get("dao.factsAndFigures.supply.totalBurned"));
        seriesIndexMap.put(getSeriesId(seriesTotalBurned), 1);

        seriesCompensation = new XYChart.Series<>();
        seriesCompensation.setName(Res.get("dao.factsAndFigures.supply.compReq"));
        seriesIndexMap.put(getSeriesId(seriesCompensation), 2);

        seriesReimbursement = new XYChart.Series<>();
        seriesReimbursement.setName(Res.get("dao.factsAndFigures.supply.reimbursement"));
        seriesIndexMap.put(getSeriesId(seriesReimbursement), 3);

        seriesBsqTradeFee = new XYChart.Series<>();
        seriesBsqTradeFee.setName(Res.get("dao.factsAndFigures.supply.bsqTradeFee"));
        seriesIndexMap.put(getSeriesId(seriesBsqTradeFee), 4);

        seriesProofOfBurn = new XYChart.Series<>();
        seriesProofOfBurn.setName(Res.get("dao.factsAndFigures.supply.proofOfBurn"));
        seriesIndexMap.put(getSeriesId(seriesProofOfBurn), 5);
    }

    @Override
    protected void addActiveSeries() {
        addSeries(seriesTotalIssued);
        addSeries(seriesTotalBurned);
    }

    private void addSeries(XYChart.Series<Number, Number> series) {
        activeSeries.add(getSeriesId(series));
        chart.getData().add(series);
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

        UserThread.execute(this::setTimeLineLabels);
    }

    @Override
    protected void updateData(Predicate<Long> predicate) {
        if (activeSeries.contains(getSeriesId(seriesBsqTradeFee))) {
            seriesBsqTradeFee.getData().setAll(model.getBsqTradeFeeChartData(predicate));
        }
        if (activeSeries.contains(getSeriesId(seriesCompensation))) {
            seriesCompensation.getData().setAll(model.getCompensationChartData(predicate));
        }

        updateOtherSeries(predicate);

        applyTooltip();
        applySeriesStyles();
    }

    @Override
    protected void activateSeries(XYChart.Series<Number, Number> series) {
        super.activateSeries(series);
        if (getSeriesId(series).equals(getSeriesId(seriesBsqTradeFee))) {
            seriesBsqTradeFee.getData().setAll(model.getBsqTradeFeeChartData(model.getPredicate()));
        } else if (getSeriesId(series).equals(getSeriesId(seriesCompensation))) {
            seriesCompensation.getData().setAll(model.getCompensationChartData(model.getPredicate()));
        } else if (getSeriesId(series).equals(getSeriesId(seriesProofOfBurn))) {
            seriesProofOfBurn.getData().setAll(model.getProofOfBurnChartData(model.getPredicate()));
        } else if (getSeriesId(series).equals(getSeriesId(seriesReimbursement))) {
            seriesReimbursement.getData().setAll(model.getReimbursementChartData(model.getPredicate()));
        } else if (getSeriesId(series).equals(getSeriesId(seriesTotalIssued))) {
            seriesTotalIssued.getData().setAll(model.getTotalIssuedChartData(model.getPredicate()));
        } else if (getSeriesId(series).equals(getSeriesId(seriesTotalBurned))) {
            seriesTotalBurned.getData().setAll(model.getTotalBurnedChartData(model.getPredicate()));
        }
    }

    private void updateOtherSeries(Predicate<Long> predicate) {
        if (activeSeries.contains(getSeriesId(seriesProofOfBurn))) {
            seriesProofOfBurn.getData().setAll(model.getProofOfBurnChartData(predicate));
        }
        if (activeSeries.contains(getSeriesId(seriesReimbursement))) {
            seriesReimbursement.getData().setAll(model.getReimbursementChartData(predicate));
        }
        if (activeSeries.contains(getSeriesId(seriesTotalIssued))) {
            seriesTotalIssued.getData().setAll(model.getTotalIssuedChartData(predicate));
        }
        if (activeSeries.contains(getSeriesId(seriesTotalBurned))) {
            seriesTotalBurned.getData().setAll(model.getTotalBurnedChartData(predicate));
        }
    }
}
