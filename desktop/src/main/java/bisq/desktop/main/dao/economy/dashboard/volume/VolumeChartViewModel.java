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

package bisq.desktop.main.dao.economy.dashboard.volume;

import bisq.desktop.components.chart.ChartViewModel;

import bisq.core.locale.GlobalSettings;

import bisq.common.util.MathUtils;

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import javafx.util.StringConverter;

import java.text.DecimalFormat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VolumeChartViewModel extends ChartViewModel<VolumeChartDataModel> {
    private Function<Number, String> yAxisFormatter = value -> value + " USD";
    private final DecimalFormat volumeFormat;


    @Inject
    public VolumeChartViewModel(VolumeChartDataModel dataModel) {
        super(dataModel);

        volumeFormat = (DecimalFormat) DecimalFormat.getNumberInstance(GlobalSettings.getLocale());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Total amounts
    ///////////////////////////////////////////////////////////////////////////////////////////

    CompletableFuture<Long> getUsdVolume() {
        return CompletableFuture.supplyAsync(dataModel::getUsdVolume);
    }

    CompletableFuture<Long> getBtcVolume() {
        return CompletableFuture.supplyAsync(dataModel::getBtcVolume);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart data
    ///////////////////////////////////////////////////////////////////////////////////////////

    CompletableFuture<List<XYChart.Data<Number, Number>>> getUsdVolumeChartData() {
        return CompletableFuture.supplyAsync(() -> toChartLongData(dataModel.getUsdVolumeByInterval()));
    }

    CompletableFuture<List<XYChart.Data<Number, Number>>> getBtcVolumeChartData() {
        return CompletableFuture.supplyAsync(() -> toChartLongData(dataModel.getBtcVolumeByInterval()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Formatters/Converters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected StringConverter<Number> getYAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return yAxisFormatter.apply(value);
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    void setUsdVolumeFormatter() {
        volumeFormat.setMaximumFractionDigits(0);
        yAxisFormatter = value -> volumeFormat.format(MathUtils.scaleDownByPowerOf10(value.longValue(), 4)) + " USD";
    }

    void setBtcVolumeFormatter() {
        volumeFormat.setMaximumFractionDigits(4);
        yAxisFormatter = value -> volumeFormat.format(MathUtils.scaleDownByPowerOf10(value.longValue(), 8)) + " BTC";
    }
}
