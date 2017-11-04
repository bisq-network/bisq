/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.bisq.gui.main.market.trades.charts.price;

import io.bisq.gui.main.market.trades.charts.CandleData;
import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Candle node used for drawing a candle
 */
public class Candle extends Group {
    private static final Logger log = LoggerFactory.getLogger(Candle.class);

    private String seriesStyleClass;
    private String dataStyleClass;
    private final CandleTooltip candleTooltip;
    private final Line highLowLine = new Line();
    private final Region bar = new Region();

    private boolean openAboveClose = true;
    private double closeOffset;

    Candle(String seriesStyleClass, String dataStyleClass, StringConverter<Number> priceStringConverter) {
        this.seriesStyleClass = seriesStyleClass;
        this.dataStyleClass = dataStyleClass;

        setAutoSizeChildren(false);
        getChildren().addAll(highLowLine, bar);
        getStyleClass().setAll("candlestick-candle", seriesStyleClass, dataStyleClass);
        updateStyleClasses();

        candleTooltip = new CandleTooltip(priceStringConverter);
        Tooltip tooltip = new Tooltip();
        tooltip.setGraphic(candleTooltip);
        Tooltip.install(this, tooltip);
    }

    public void setSeriesAndDataStyleClasses(String seriesStyleClass, String dataStyleClass) {
        this.seriesStyleClass = seriesStyleClass;
        this.dataStyleClass = dataStyleClass;
        getStyleClass().setAll("candlestick-candle", seriesStyleClass, dataStyleClass);
        updateStyleClasses();
    }

    public void update(double closeOffset, double highOffset, double lowOffset, double candleWidth) {
        this.closeOffset = closeOffset;
        openAboveClose = closeOffset > 0;
        updateStyleClasses();
        highLowLine.setStartY(highOffset);
        highLowLine.setEndY(lowOffset);
        if (openAboveClose) {
            bar.resizeRelocate(-candleWidth / 2, 0, candleWidth, Math.max(5, closeOffset));
        } else {
            bar.resizeRelocate(-candleWidth / 2, closeOffset, candleWidth, Math.max(5, closeOffset * -1));
        }
    }

    public void updateTooltip(CandleData candleData) {
        candleTooltip.update(candleData);
    }

    private void updateStyleClasses() {
        String style = openAboveClose ? "open-above-close" : "close-above-open";
        if (closeOffset == 0)
            style = "empty";

        highLowLine.getStyleClass().setAll("candlestick-line", seriesStyleClass, dataStyleClass,
            style);

        bar.getStyleClass().setAll("candlestick-bar", seriesStyleClass, dataStyleClass,
            style);
    }
}
