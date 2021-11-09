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

package bisq.desktop.main.dao.economy.dashboard.price;

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
public class PriceChartViewModel extends ChartViewModel<PriceChartDataModel> {
    private Function<Number, String> yAxisFormatter = value -> value + " BSQ/USD";
    private final DecimalFormat priceFormat;

    @Inject
    public PriceChartViewModel(PriceChartDataModel dataModel) {
        super(dataModel);

        priceFormat = (DecimalFormat) DecimalFormat.getNumberInstance(GlobalSettings.getLocale());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Average price from timeline selection
    ///////////////////////////////////////////////////////////////////////////////////////////

    CompletableFuture<Double> averageBsqUsdPrice() {
        return CompletableFuture.supplyAsync(() -> dataModel.averageBsqUsdPrice());
    }

    CompletableFuture<Double> averageBsqBtcPrice() {
        return CompletableFuture.supplyAsync(() -> dataModel.averageBsqBtcPrice());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart data
    ///////////////////////////////////////////////////////////////////////////////////////////


    CompletableFuture<List<XYChart.Data<Number, Number>>> getBsqUsdPriceChartData() {
        return CompletableFuture.supplyAsync(() -> toChartDoubleData(dataModel.getBsqUsdPriceByInterval()));
    }

    CompletableFuture<List<XYChart.Data<Number, Number>>> getBsqBtcPriceChartData() {
        return CompletableFuture.supplyAsync(() -> toChartDoubleData(dataModel.getBsqBtcPriceByInterval()));
    }

    CompletableFuture<List<XYChart.Data<Number, Number>>> getBtcUsdPriceChartData() {
        return CompletableFuture.supplyAsync(() -> toChartDoubleData(dataModel.getBtcUsdPriceByInterval()));
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

    void setBsqUsdPriceFormatter() {
        priceFormat.setMaximumFractionDigits(4);
        yAxisFormatter = value -> priceFormat.format(value) + " BSQ/USD";
    }

    void setBsqBtcPriceFormatter() {
        priceFormat.setMaximumFractionDigits(8);
        yAxisFormatter = value -> {
            value = MathUtils.scaleDownByPowerOf10(value.longValue(), 8);
            return priceFormat.format(value) + " BSQ/BTC";
        };
    }

    void setBtcUsdPriceFormatter() {
        priceFormat.setMaximumFractionDigits(0);
        yAxisFormatter = value -> priceFormat.format(value) + " BTC/USD";
    }
}
