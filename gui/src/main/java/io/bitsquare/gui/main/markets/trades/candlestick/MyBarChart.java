package io.bitsquare.gui.main.markets.trades.candlestick;

import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.BarChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBarChart<X, Y> extends BarChart<X, Y> {
    private static final Logger log = LoggerFactory.getLogger(MyBarChart.class);

    public MyBarChart(Axis<X> xAxis, Axis<Y> yAxis) {
        super(xAxis, yAxis);
    }

    public MyBarChart(Axis<X> xAxis, Axis<Y> yAxis, ObservableList<Series<X, Y>> data) {
        super(xAxis, yAxis, data);
    }

    public MyBarChart(Axis<X> xAxis, Axis<Y> yAxis, ObservableList<Series<X, Y>> data, double categoryGap) {
        super(xAxis, yAxis, data, categoryGap);
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        if (getPlotChildren().contains(item.getNode()))
            getPlotChildren().remove(item.getNode());

        super.dataItemAdded(series, itemIndex, item);
    }
}
