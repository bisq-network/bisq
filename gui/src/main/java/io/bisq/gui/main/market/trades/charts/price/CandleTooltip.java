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

import io.bisq.common.locale.Res;
import io.bisq.gui.main.market.trades.charts.CandleData;
import io.bisq.gui.util.Layout;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

/**
 * The content for Candle tool tips
 */
public class CandleTooltip extends GridPane {
    private final StringConverter<Number> priceStringConverter;
    private final Label openValue = new Label();
    private final Label closeValue = new Label();
    private final Label highValue = new Label();
    private final Label lowValue = new Label();
    private final Label averageValue = new Label();
    private final Label dateValue = new Label();

    CandleTooltip(StringConverter<Number> priceStringConverter) {
        this.priceStringConverter = priceStringConverter;

        setHgap(Layout.GRID_GAP);

        setVgap(2);

        Label open = new Label(Res.get("market.trades.tooltip.candle.open"));
        Label close = new Label(Res.get("market.trades.tooltip.candle.close"));
        Label high = new Label(Res.get("market.trades.tooltip.candle.high"));
        Label low = new Label(Res.get("market.trades.tooltip.candle.low"));
        Label average = new Label(Res.get("market.trades.tooltip.candle.average"));
        Label date = new Label(Res.get("market.trades.tooltip.candle.date"));
        setConstraints(open, 0, 0);
        setConstraints(openValue, 1, 0);
        setConstraints(close, 0, 1);
        setConstraints(closeValue, 1, 1);
        setConstraints(high, 0, 2);
        setConstraints(highValue, 1, 2);
        setConstraints(low, 0, 3);
        setConstraints(lowValue, 1, 3);
        setConstraints(average, 0, 4);
        setConstraints(averageValue, 1, 4);
        setConstraints(date, 0, 5);
        setConstraints(dateValue, 1, 5);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.NEVER);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(columnConstraints1, columnConstraints2);

        getChildren().addAll(open, openValue, close, closeValue, high, highValue, low, lowValue, average, averageValue, date, dateValue);
    }

    public void update(CandleData candleData) {
        openValue.setText(priceStringConverter.toString(candleData.open));
        closeValue.setText(priceStringConverter.toString(candleData.close));
        highValue.setText(priceStringConverter.toString(candleData.high));
        lowValue.setText(priceStringConverter.toString(candleData.low));
        averageValue.setText(priceStringConverter.toString(candleData.average));
        dateValue.setText(candleData.date);
    }
}