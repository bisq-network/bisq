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

package bisq.desktop.util;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MovingAverageUtils {

    /* With period 2, on an input of [1,2,3,4], should return [Double.NaN, 1.5, 2.5, 3.5].
     *
     * In case of the source stream having too few elements to compute a moving average
     * (as a function of the provided period), the returned stream will (only) contain
     * a sequence of (period - 1) NaNs. Otherwise, the resulting stream is prepadded with
     * these NaNs. See `prependLagCompensation` for details.
     */
    public static Stream<Double> simpleMovingAverage(Stream<Number> source, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Simple moving average period must be a positive number.");
        }

        var windows = SlidingWindowSpliterator.windowed(source, period);
        Stream<Double> averages =
                windows.map(window ->
                        window
                                .mapToDouble(Number::doubleValue)
                                .summaryStatistics()
                                .getAverage()
                );

        return prependLagCompensation(averages, period);
    }

    /* Given a period of for example 3, prepends a sequence of 2 NaNs.
     * In this way the returned stream has the same length as the input stream,
     * and the index of a given average matches the index of the last element
     * of a sequence of data points from which the average was computed,
     * Provided there were enough data points in the input stream to compute
     * the moving average (see next paragraph).
     *
     * Unfortunately, if there are too little data points to calculate the
     * moving average, this will return a stream with more elements, that are
     * all NaNs, than the input stream contained. This is due to the inherent
     * laziness of streams: we cannot check the relevant streams' sizes
     * without destroying them, so we cannot make the prepadding adaptive.
     * The exact number of NaNs returned in this case is `period - 1`.
     */
    private static Stream<Double> prependLagCompensation(Stream<Double> averages, int period) {
        var lag = period - 1;
        var lagCompensation = Collections.nCopies(lag, Double.NaN).stream();
        return Stream.concat(lagCompensation, averages);
    }

    static class SlidingWindowSpliterator<T> implements Spliterator<Stream<T>> {

        static <T> Stream<Stream<T>> windowed(Stream<T> source, int windowSize) {
            return StreamSupport.stream(new SlidingWindowSpliterator<>(source, windowSize), false);
        }

        private final Queue<T> buffer;
        private final Iterator<T> sourceIterator;
        private final int windowSize;

        private SlidingWindowSpliterator(Stream<T> source, int windowSize) {
            this.buffer = new ArrayDeque<>(windowSize);
            this.sourceIterator = Objects.requireNonNull(source).iterator();
            this.windowSize = windowSize;
        }

        @Override
        public boolean tryAdvance(Consumer<? super Stream<T>> action) {
            if (windowSize < 1) {
                return false;
            }

            while (sourceIterator.hasNext()) {
                buffer.add(sourceIterator.next());

                if (buffer.size() == windowSize) {
                    action.accept(Arrays.stream((T[]) buffer.toArray(new Object[0])));
                    buffer.poll();
                    return true;
                }
            }

            return false;
        }

        @Override
        public Spliterator<Stream<T>> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL;
        }
    }

}
