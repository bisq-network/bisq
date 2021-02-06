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

import bisq.desktop.common.model.ActivatableViewModel;

import bisq.common.util.Tuple2;

import java.time.temporal.TemporalAdjuster;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ChartModel extends ActivatableViewModel {
    public interface Listener {
        /**
         * @param fromDate      Epoch date in millis for earliest data
         * @param toDate        Epoch date in millis for latest data
         */
        void onDateFilterChanged(long fromDate, long toDate);
    }

    protected Number lowerBound, upperBound;
    protected final Set<Listener> listeners = new HashSet<>();

    private Predicate<Long> predicate = e -> true;

    public ChartModel() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public Number getLowerBound() {
        return lowerBound;
    }

    public Number getUpperBound() {
        return upperBound;
    }

    Tuple2<Double, Double> timelinePositionToEpochSeconds(double leftPos, double rightPos) {
        long lowerBoundAsLong = lowerBound.longValue();
        long totalRange = upperBound.longValue() - lowerBoundAsLong;
        double fromDateSec = lowerBoundAsLong + totalRange * leftPos;
        double toDateSec = lowerBoundAsLong + totalRange * rightPos;
        return new Tuple2<>(fromDateSec, toDateSec);
    }

    protected abstract void applyTemporalAdjuster(TemporalAdjuster temporalAdjuster);

    protected abstract TemporalAdjuster getTemporalAdjuster();

    Predicate<Long> getPredicate() {
        return predicate;
    }

    Predicate<Long> setAndGetPredicate(Tuple2<Double, Double> fromToTuple) {
        predicate = value -> value >= fromToTuple.first && value <= fromToTuple.second;
        return predicate;
    }

    void notifyListeners(Tuple2<Double, Double> fromToTuple) {
        // We use millis for our listeners
        long first = fromToTuple.first.longValue() * 1000;
        long second = fromToTuple.second.longValue() * 1000;
        listeners.forEach(l -> l.onDateFilterChanged(first, second));
    }

}
