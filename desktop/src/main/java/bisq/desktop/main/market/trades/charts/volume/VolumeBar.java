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

package bisq.desktop.main.market.trades.charts.volume;

import bisq.desktop.main.market.trades.charts.CandleData;

import bisq.core.locale.Res;
import bisq.core.util.VolumeUtil;

import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

import javafx.util.StringConverter;

public class VolumeBar extends Group {
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
        String volumeInBtc = volumeStringConverter.toString(candleData.accumulatedAmount);
        String volumeInUsd = VolumeUtil.formatLargeFiat(candleData.volumeInUsd, "USD");
        tooltip.setText(Res.get("market.trades.tooltip.volumeBar", volumeInBtc, volumeInUsd, candleData.numTrades, candleData.date));
    }

    private void updateStyleClasses() {
        bar.getStyleClass().setAll("volume-bar", seriesStyleClass, dataStyleClass, "bg");
    }
}
