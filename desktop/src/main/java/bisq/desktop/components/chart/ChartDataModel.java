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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ChartDataModel extends ActivatableDataModel {
    protected final TemporalAdjusterModel temporalAdjusterModel = new TemporalAdjusterModel();
    protected LongPredicate dateFilter = e -> true;


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

    // optimized for use when the input times are sequential and not too spread out
    public ToLongFunction<Instant> toCachedTimeIntervalFn() {
        return temporalAdjusterModel.withCache()::toTimeInterval;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Date filter predicate
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LongPredicate getDateFilter() {
        return dateFilter;
    }

    void setDateFilter(long from, long to) {
        dateFilter = value -> value >= from && value <= to;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void invalidateCache();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected static <T, R> Function<T, R> memoize(Function<T, R> fn) {
        Map<T, R> map = new ConcurrentHashMap<>();
        return x -> map.computeIfAbsent(x, fn);
    }

    protected static <V> Map<Long, V> getMergedMap(Map<Long, V> map1,
                                                   Map<Long, V> map2,
                                                   BinaryOperator<V> mergeFunction) {
        return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, mergeFunction));
    }
}
