/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */
package io.bitsquare.gui.main.market.trades.charts.volume;

import io.bitsquare.gui.main.market.trades.charts.CandleData;
import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VolumeBar extends Group {
    private static final Logger log = LoggerFactory.getLogger(VolumeBar.class);

    private String seriesStyleClass;
    private String dataStyleClass;
    private final StringConverter<Number> volumeStringConverter;

    private final Region bar = new Region();
    private final Tooltip tooltip;

    VolumeBar(String seriesStyleClass, String dataStyleClass, StringConverter<Number> volumeStringConverter) {
        this.seriesStyleClass = seriesStyleClass;
        this.dataStyleClass = dataStyleClass;
        this.volumeStringConverter = volumeStringConverter;

        setAutoSizeChildren(false);
        getChildren().add(bar);
        updateStyleClasses();
        tooltip = new Tooltip();
        Tooltip.install(this, tooltip);
    }

    public void setSeriesAndDataStyleClasses(String seriesStyleClass, String dataStyleClass) {
        this.seriesStyleClass = seriesStyleClass;
        this.dataStyleClass = dataStyleClass;
        updateStyleClasses();
    }

    public void update(double height, double candleWidth, CandleData candleData) {
        bar.resizeRelocate(-candleWidth / 2, 0, candleWidth, height);
        tooltip.setText("Volume: " + volumeStringConverter.toString(candleData.accumulatedAmount) + "\n" +
                "No. of trades: " + candleData.numTrades + "\n" +
                "Date: " + candleData.date);
    }

    private void updateStyleClasses() {
        bar.getStyleClass().setAll("volume-bar", seriesStyleClass, dataStyleClass, "bg");
    }
}