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

package bisq.desktop.main.dao.economy.supply.dao;

import bisq.desktop.components.chart.ChartViewModel;

import bisq.core.locale.GlobalSettings;
import bisq.core.util.coin.BsqFormatter;

import javax.inject.Inject;

import javafx.scene.chart.XYChart;

import javafx.util.StringConverter;

import java.text.DecimalFormat;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoChartViewModel extends ChartViewModel<DaoChartDataModel> {
    private final DecimalFormat priceFormat;
    private final BsqFormatter bsqFormatter;


    @Inject
    public DaoChartViewModel(DaoChartDataModel dataModel, BsqFormatter bsqFormatter) {
        super(dataModel);

        this.bsqFormatter = bsqFormatter;
        priceFormat = (DecimalFormat) DecimalFormat.getNumberInstance(GlobalSettings.getLocale());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Chart data
    ///////////////////////////////////////////////////////////////////////////////////////////

    List<XYChart.Data<Number, Number>> getTotalIssuedChartData() {
        return toChartData(dataModel.getTotalIssuedByInterval());
    }

    List<XYChart.Data<Number, Number>> getCompensationChartData() {
        return toChartData(dataModel.getCompensationByInterval());
    }

    List<XYChart.Data<Number, Number>> getReimbursementChartData() {
        return toChartData(dataModel.getReimbursementByInterval());
    }

    List<XYChart.Data<Number, Number>> getTotalBurnedChartData() {
        return toChartData(dataModel.getTotalBurnedByInterval());
    }

    List<XYChart.Data<Number, Number>> getBsqTradeFeeChartData() {
        return toChartData(dataModel.getBsqTradeFeeByInterval());
    }

    List<XYChart.Data<Number, Number>> getProofOfBurnChartData() {
        return toChartData(dataModel.getProofOfBurnByInterval());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Formatters/Converters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected StringConverter<Number> getYAxisStringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return priceFormat.format(Double.parseDouble(bsqFormatter.formatBSQSatoshis(value.longValue()))) + " BSQ";
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
    }

    @Override
    protected String getTooltipValueConverter(Number value) {
        return bsqFormatter.formatBSQSatoshisWithCode(value.longValue());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoChartDataModel delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    long getCompensationAmount() {
        return dataModel.getCompensationAmount();
    }

    long getReimbursementAmount() {
        return dataModel.getReimbursementAmount();
    }

    long getBsqTradeFeeAmount() {
        return dataModel.getBsqTradeFeeAmount();
    }

    long getProofOfBurnAmount() {
        return dataModel.getProofOfBurnAmount();
    }
}
