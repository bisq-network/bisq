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

package bisq.desktop.components.chart;

import bisq.desktop.common.model.ActivatableDataModel;

import java.time.Instant;
import java.time.temporal.TemporalAdjuster;

import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ChartDataModel extends ActivatableDataModel {
    protected final TemporalAdjusterModel temporalAdjusterModel = new TemporalAdjusterModel();
    protected Predicate<Long> dateFilter = e -> true;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ChartDataModel() {
        super();
    }

    @Override
    public void activate() {
        dateFilter = e -> true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TemporalAdjusterModel delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setTemporalAdjuster(TemporalAdjuster temporalAdjuster) {
        temporalAdjusterModel.setTemporalAdjuster(temporalAdjuster);
    }

    TemporalAdjuster getTemporalAdjuster() {
        return temporalAdjusterModel.getTemporalAdjuster();
    }

    public long toTimeInterval(Instant instant) {
        return temporalAdjusterModel.toTimeInterval(instant);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Date filter predicate
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Predicate<Long> getDateFilter() {
        return dateFilter;
    }

    void setDateFilter(long from, long to) {
        dateFilter = value -> value >= from && value <= to;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void invalidateCache();

    protected Map<Long, Long> getMergedMap(Map<Long, Long> map1,
                                           Map<Long, Long> map2,
                                           BinaryOperator<Long> mergeFunction) {
        return Stream.concat(map1.entrySet().stream(),
                map2.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        mergeFunction));
    }
}
