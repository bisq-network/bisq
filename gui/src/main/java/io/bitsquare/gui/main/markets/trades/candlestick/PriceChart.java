package io.bitsquare.gui.main.markets.trades.candlestick;

import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PriceChart<X, Y> extends LineChart<X, Y> {
    private static final Logger log = LoggerFactory.getLogger(PriceChart.class);

    public PriceChart(Axis axis, Axis axis2) {
        super(axis, axis2);
    }

    public PriceChart(Axis axis, Axis axis2, ObservableList data) {
        super(axis, axis2, data);
    }
}
