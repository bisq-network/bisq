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
import bisq.desktop.main.dao.economy.supply.DaoEconomyDataProvider;
import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.UserThread;

import javax.inject.Inject;

import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;

import javafx.geometry.Side;

import javafx.collections.ListChangeListener;

import javafx.util.StringConverter;

import java.time.Instant;

import java.text.DecimalFormat;

import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoEconomyChartView extends ChartView<DaoEconomyChartModel> {
    private static final DecimalFormat priceFormat = new DecimalFormat(",###");
    private final BsqFormatter bsqFormatter;

    private XYChart.Series<Number, Number> seriesBsqTradeFee, seriesProofOfBurn, seriesCompensation,
            seriesReimbursement/*, seriesBtcTradeFee*/;
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

        // Turn off some series
        hideSeries(seriesProofOfBurn);
        hideSeries(seriesReimbursement);
        /* hideSeries(seriesBtcTradeFee);*/

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
                Date date = new Date(DaoEconomyDataProvider.toStartOfMonth(Instant.ofEpochSecond(epochSeconds.longValue())) * 1000);
                return DisplayUtils.formatDateAxis(date, "dd/MMM\nyyyy");
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
    protected void addSeries() {
        seriesBsqTradeFee = new XYChart.Series<>();
        seriesBsqTradeFee.setName(Res.get("dao.factsAndFigures.supply.bsqTradeFee"));
        seriesIndexMap.put(seriesBsqTradeFee.getName(), 0);
        chart.getData().add(seriesBsqTradeFee);

      /*  seriesBtcTradeFee = new XYChart.Series<>();
        seriesBtcTradeFee.setName(Res.get("dao.factsAndFigures.supply.btcTradeFee"));
        seriesIndexMap.put(seriesBtcTradeFee.getName(), 4);
        chart.getData().add(seriesBtcTradeFee);*/

        seriesCompensation = new XYChart.Series<>();
        seriesCompensation.setName(Res.get("dao.factsAndFigures.supply.compReq"));
        seriesIndexMap.put(seriesCompensation.getName(), 1);
        chart.getData().add(seriesCompensation);

        seriesProofOfBurn = new XYChart.Series<>();
        seriesProofOfBurn.setName(Res.get("dao.factsAndFigures.supply.proofOfBurn"));
        seriesIndexMap.put(seriesProofOfBurn.getName(), 2);
        chart.getData().add(seriesProofOfBurn);

        seriesReimbursement = new XYChart.Series<>();
        seriesReimbursement.setName(Res.get("dao.factsAndFigures.supply.reimbursement"));
        seriesIndexMap.put(seriesReimbursement.getName(), 3);
        chart.getData().add(seriesReimbursement);
    }

    @Override
    protected void initData() {
        List<XYChart.Data<Number, Number>> bsqTradeFeeChartData = model.getBsqTradeFeeChartData(e -> true);
        seriesBsqTradeFee.getData().setAll(bsqTradeFeeChartData);

    /*    List<XYChart.Data<Number, Number>> btcTradeFeeChartData = model.getBtcTradeFeeChartData(e -> true);
        seriesBtcTradeFee.getData().setAll(btcTradeFeeChartData);*/

        List<XYChart.Data<Number, Number>> compensationRequestsChartData = model.getCompensationChartData(e -> true);
        seriesCompensation.getData().setAll(compensationRequestsChartData);

        List<XYChart.Data<Number, Number>> proofOfBurnChartData = model.getProofOfBurnChartData(e -> true);
        seriesProofOfBurn.getData().setAll(proofOfBurnChartData);

        List<XYChart.Data<Number, Number>> reimbursementChartData = model.getReimbursementChartData(e -> true);
        seriesReimbursement.getData().setAll(reimbursementChartData);

        applyTooltip();

        // We don't need redundant data like reimbursementChartData as time value from compensationRequestsChartData
        // will cover it
        model.initBounds(bsqTradeFeeChartData, compensationRequestsChartData);
        xAxis.setLowerBound(model.getLowerBound().doubleValue());
        xAxis.setUpperBound(model.getUpperBound().doubleValue());

        UserThread.execute(this::setTimeLineLabels);
    }

    @Override
    protected void updateData(Predicate<Long> predicate) {
        List<XYChart.Data<Number, Number>> tradeFeeChartData = model.getBsqTradeFeeChartData(predicate);
        seriesBsqTradeFee.getData().setAll(tradeFeeChartData);

   /*     List<XYChart.Data<Number, Number>> btcTradeFeeChartData = model.getBtcTradeFeeChartData(predicate);
        seriesBtcTradeFee.getData().setAll(btcTradeFeeChartData);*/

        List<XYChart.Data<Number, Number>> compensationRequestsChartData = model.getCompensationChartData(predicate);
        seriesCompensation.getData().setAll(compensationRequestsChartData);

        List<XYChart.Data<Number, Number>> proofOfBurnChartData = model.getProofOfBurnChartData(predicate);
        seriesProofOfBurn.getData().setAll(proofOfBurnChartData);

        List<XYChart.Data<Number, Number>> reimbursementChartData = model.getReimbursementChartData(predicate);
        seriesReimbursement.getData().setAll(reimbursementChartData);

        applyTooltip();
    }

    @Override
    protected void applyTooltip() {
        chart.getData().forEach(series -> {
            String format = series == seriesCompensation || series == seriesReimbursement ?
                    "dd MMM yyyy" :
                    "MMM yyyy";
            series.getData().forEach(data -> {
                String xValue = DisplayUtils.formatDateAxis(new Date(data.getXValue().longValue() * 1000), format);
                String yValue = bsqFormatter.formatBSQSatoshisWithCode(data.getYValue().longValue());
                Node node = data.getNode();
                if (node == null) {
                    return;
                }
                Tooltip.install(node, new Tooltip(Res.get("dao.factsAndFigures.supply.chart.tradeFee.toolTip", yValue, xValue)));
            });
        });
    }
}
