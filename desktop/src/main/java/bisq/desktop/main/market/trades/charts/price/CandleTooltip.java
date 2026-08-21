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

package bisq.desktop.main.market.trades.charts.price;

import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.main.market.trades.charts.CandleData;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;

import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;

import javafx.util.StringConverter;

/**
 * The content for Candle tool tips
 */
public class CandleTooltip extends GridPane {
    private final StringConverter<Number> priceStringConverter;
    private final Label openValue = new AutoTooltipLabel();
    private final Label closeValue = new AutoTooltipLabel();
    private final Label highValue = new AutoTooltipLabel();
    private final Label lowValue = new AutoTooltipLabel();
    private final Label averageValue = new AutoTooltipLabel();
    private final Label medianValue = new AutoTooltipLabel();
    private final Label dateValue = new AutoTooltipLabel();

    CandleTooltip(StringConverter<Number> priceStringConverter) {
        this.priceStringConverter = priceStringConverter;

        setHgap(Layout.GRID_GAP);

        setVgap(2);

        Label open = new AutoTooltipLabel(Res.get("market.trades.tooltip.candle.open"));
        Label close = new AutoTooltipLabel(Res.get("market.trades.tooltip.candle.close"));
        Label high = new AutoTooltipLabel(Res.get("market.trades.tooltip.candle.high"));
        Label low = new AutoTooltipLabel(Res.get("market.trades.tooltip.candle.low"));
        Label average = new AutoTooltipLabel(Res.get("market.trades.tooltip.candle.average"));
        Label median = new AutoTooltipLabel(Res.get("market.trades.tooltip.candle.median"));
        Label date = new AutoTooltipLabel(Res.get("market.trades.tooltip.candle.date"));
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
        setConstraints(median, 0, 5);
        setConstraints(medianValue, 1, 5);
        setConstraints(date, 0, 6);
        setConstraints(dateValue, 1, 6);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.NEVER);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(columnConstraints1, columnConstraints2);

        getChildren().addAll(open, openValue, close, closeValue, high, highValue, low, lowValue, average, averageValue, median, medianValue, date, dateValue);
    }

    public void update(CandleData candleData) {
        openValue.setText(priceStringConverter.toString(candleData.open));
        closeValue.setText(priceStringConverter.toString(candleData.close));
        highValue.setText(priceStringConverter.toString(candleData.high));
        lowValue.setText(priceStringConverter.toString(candleData.low));
        averageValue.setText(priceStringConverter.toString(candleData.average));
        medianValue.setText(priceStringConverter.toString(candleData.median));
        dateValue.setText(candleData.date);
    }
}
