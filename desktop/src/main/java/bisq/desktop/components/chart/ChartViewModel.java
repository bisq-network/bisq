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

package bisq.desktop.components.chart;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.util.DisplayUtils;

import bisq.common.util.Tuple2;

import javafx.scene.chart.XYChart;

import javafx.util.StringConverter;

import java.time.temporal.TemporalAdjuster;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ChartViewModel<T extends ChartDataModel> extends ActivatableWithDataModel<T> {
    private final static double LEFT_TIMELINE_SNAP_VALUE = 0.01;
    private final static double RIGHT_TIMELINE_SNAP_VALUE = 0.99;

    @Getter
    private final Double[] dividerPositions = new Double[]{0d, 1d};
    @Getter
    protected Number lowerBound;
    @Getter
    protected Number upperBound;
    @Getter
    protected String dateFormatPatters = "dd MMM\nyyyy";
    @Getter
    long fromDate;
    @Getter
    long toDate;

    public ChartViewModel(T dataModel) {
        super(dataModel);
    }

    @Override
    public void activate() {
        dividerPositions[0] = 0d;
        dividerPositions[1] = 1d;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TimerInterval/TemporalAdjuster
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void applyTemporalAdjuster(TemporalAdjuster temporalAdjuster) {
        dataModel.setTemporalAdjuster(temporalAdjuster);
    }

    void setDateFormatPattern(TemporalAdjusterModel.Interval interval) {
        switch (interval) {
            case YEAR:
                dateFormatPatters = "yyyy";
                break;
            case MONTH:
                dateFormatPatters = "MMM\nyyyy";
                break;
            default:
                dateFormatPatters = "MMM dd\nyyyy";
                break;
        }
    }

    protected TemporalAdjuster getTemporalAdjuster() {
        return dataModel.getTemporalAdjuster();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TimelineNavigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTimelineNavigationChanged(double leftPos, double rightPos) {
        applyFromToDates(leftPos, rightPos);
        // TODO find better solution
        // The TemporalAdjusters map dates to the lower bound (e.g. 1.1.2016) but our from date is the date of
        // the first data entry so if we filter by that we would exclude the first year data in case YEAR was selected
        // A trade with data 3.May.2016 gets mapped to 1.1.2016 and our from date will be April 2016, so we would
        // filter that. It is a bit tricky to sync the TemporalAdjusters with our date filter. To include at least in
        // the case when we have not set the date filter (left =0 / right =1) we set from date to epoch time 0 and
        // to date to one year ahead to be sure we include all.

        long from, to;

        // We only manipulate the from, to variables for the date filter, not the fromDate, toDate properties as those
        // are used by the view for tooltip over the time line navigation dividers
        if (leftPos < LEFT_TIMELINE_SNAP_VALUE) {
            from = 0;
        } else {
            from = fromDate;
        }
        if (rightPos > RIGHT_TIMELINE_SNAP_VALUE) {
            to = new Date().getTime() / 1000 + TimeUnit.DAYS.toSeconds(365);
        } else {
            to = toDate;
        }

        dividerPositions[0] = leftPos;
        dividerPositions[1] = rightPos;
        dataModel.setDateFilter(from, to);
    }

    void applyFromToDates(double leftPos, double rightPos) {
        // We need to snap into the 0 and 1 values once we are close as otherwise once navigation has been used we
        // would not get back to exact 0 or 1. Not clear why but might be rounding issues from values at x positions of
        // drag operations.
        if (leftPos < LEFT_TIMELINE_SNAP_VALUE) {
            leftPos = 0;
        }
        if (rightPos > RIGHT_TIMELINE_SNAP_VALUE) {
            rightPos = 1;
        }

        long lowerBoundAsLong = lowerBound.longValue();
        long totalRange = upperBound.longValue() - lowerBoundAsLong;

        fromDate = (long) (lowerBoundAsLong + totalRange * leftPos);
        toDate = (long) (lowerBoundAsLong + totalRange * rightPos);
    }

    void onTimelineMouseDrag(double leftPos, double rightPos) {
        // Limit drag operation if we have hit a boundary
        if (leftPos > LEFT_TIMELINE_SNAP_VALUE) {
            dividerPositions[1] = rightPos;
        }
        if (rightPos < RIGHT_TIMELINE_SNAP_VALUE) {
            dividerPositions[0] = leftPos;
        }
    }

    void initBounds(List<XYChart.Data<Number, Number>> data1,
                    List<XYChart.Data<Number, Number>> data2) {
        Tuple2<Double, Double> xMinMaxTradeFee = getMinMax(data1);
        Tuple2<Double, Double> xMinMaxCompensationRequest = getMinMax(data2);

        lowerBound = Math.min(xMinMaxTradeFee.first, xMinMaxCompensationRequest.first);
        upperBound = Math.max(xMinMaxTradeFee.second, xMinMaxCompensationRequest.second);
    }

    void initBounds(List<XYChart.Data<Number, Number>> data) {
        Tuple2<Double, Double> xMinMaxTradeFee = getMinMax(data);
        lowerBound = xMinMaxTradeFee.first;
        upperBound = xMinMaxTradeFee.second;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    StringConverter<Number> getTimeAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number epochSeconds) {
                Date date = new Date(epochSeconds.longValue() * 1000);
                return DisplayUtils.formatDateAxis(date, getDateFormatPatters());
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        };
    }

    protected StringConverter<Number> getYAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return String.valueOf(value);
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    String getTooltipDateConverter(Number date) {
        return getTimeAxisStringConverter().toString(date).replace("\n", " ");
    }

    protected String getTooltipValueConverter(Number value) {
        return getYAxisStringConverter().toString(value);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void invalidateCache() {
        dataModel.invalidateCache();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected List<XYChart.Data<Number, Number>> toChartData(Map<Long, Long> map) {
        return map.entrySet().stream()
                .map(entry -> new XYChart.Data<Number, Number>(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    protected List<XYChart.Data<Number, Number>> toChartDoubleData(Map<Long, Double> map) {
        return map.entrySet().stream()
                .map(entry -> new XYChart.Data<Number, Number>(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    protected List<XYChart.Data<Number, Number>> toChartLongData(Map<Long, Long> map) {
        return map.entrySet().stream()
                .map(entry -> new XYChart.Data<Number, Number>(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private Tuple2<Double, Double> getMinMax(List<XYChart.Data<Number, Number>> chartData) {
        long min = Long.MAX_VALUE, max = 0;
        for (XYChart.Data<Number, ?> data : chartData) {
            long value = data.getXValue().longValue();
            min = Math.min(value, min);
            max = Math.max(value, max);
        }
        return new Tuple2<>((double) min, (double) max);
    }
}
