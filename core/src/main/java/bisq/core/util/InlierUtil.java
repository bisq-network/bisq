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

package bisq.core.util;

import bisq.common.util.DoubleSummaryStatisticsWithStdDev;
import bisq.common.util.Tuple2;

import javafx.collections.FXCollections;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

public class InlierUtil {

    /* Finds the minimum and maximum inlier values. The returned values may be NaN.
     * See `computeInlierThreshold` for the definition of inlier.
     */
    public static Tuple2<Double, Double> findInlierRange(
            List<Double> yValues,
            double percentToTrim,
            double howManyStdDevsConstituteOutlier
    ) {
        Tuple2<Double, Double> inlierThreshold =
                computeInlierThreshold(yValues, percentToTrim, howManyStdDevsConstituteOutlier);

        DoubleSummaryStatistics inlierStatistics =
                yValues
                        .stream()
                        .filter(y -> withinBounds(inlierThreshold, y))
                        .mapToDouble(Double::doubleValue)
                        .summaryStatistics();

        var inlierMin = inlierStatistics.getMin();
        var inlierMax = inlierStatistics.getMax();

        return new Tuple2<>(inlierMin, inlierMax);
    }

    private static boolean withinBounds(Tuple2<Double, Double> bounds, double number) {
        var lowerBound = bounds.first;
        var upperBound = bounds.second;
        return (lowerBound <= number) && (number <= upperBound);
    }

    /* Computes the lower and upper inlier thresholds. A point lying outside
     * these thresholds is considered an outlier, and a point lying within
     * is considered an inlier.
     * The thresholds are found by trimming the dataset (see method `trim`),
     * then adding or subtracting a multiple of its (trimmed) standard
     * deviation from its (trimmed) mean.
     */
    private static Tuple2<Double, Double> computeInlierThreshold(
            List<Double> numbers, double percentToTrim, double howManyStdDevsConstituteOutlier
    ) {
        if (howManyStdDevsConstituteOutlier <= 0) {
            throw new IllegalArgumentException(
                    "howManyStdDevsConstituteOutlier should be a positive number");
        }

        List<Double> trimmed = trim(percentToTrim, numbers);

        DoubleSummaryStatisticsWithStdDev summaryStatistics =
                trimmed.stream()
                        .collect(
                                DoubleSummaryStatisticsWithStdDev::new,
                                DoubleSummaryStatisticsWithStdDev::accept,
                                DoubleSummaryStatisticsWithStdDev::combine);

        double mean = summaryStatistics.getAverage();
        double stdDev = summaryStatistics.getStandardDeviation();

        var inlierLowerThreshold = mean - (stdDev * howManyStdDevsConstituteOutlier);
        var inlierUpperThreshold = mean + (stdDev * howManyStdDevsConstituteOutlier);

        return new Tuple2<>(inlierLowerThreshold, inlierUpperThreshold);
    }

    /* Sorts the data and discards given percentage from the left and right sides each.
     * E.g. 5% trim implies a total of 10% (2x 5%) of elements discarded.
     * Used in calculating trimmed mean (and in turn trimmed standard deviation),
     * which is more robust to outliers than a simple mean.
     */
    private static List<Double> trim(double percentToTrim, List<Double> numbers) {
        var minPercentToTrim = 0;
        var maxPercentToTrim = 50;
        if (minPercentToTrim > percentToTrim || percentToTrim > maxPercentToTrim) {
            throw new IllegalArgumentException(
                    String.format(
                            "The percentage of data points to trim must be in the range [%d,%d].",
                            minPercentToTrim, maxPercentToTrim));
        }

        var totalPercentTrim = percentToTrim * 2;
        if (totalPercentTrim == 0) {
            return numbers;
        }
        if (totalPercentTrim == 100) {
            return FXCollections.emptyObservableList();
        }

        if (numbers.isEmpty()) {
            return numbers;
        }

        var count = numbers.size();
        int countToDropFromEachSide = (int) Math.round((count / 100d) * percentToTrim); // visada >= 0?
        if (countToDropFromEachSide == 0) {
            return numbers;
        }

        var sorted = numbers.stream().sorted();

        var oneSideTrimmed = sorted.skip(countToDropFromEachSide);

        // Here, having already trimmed the left-side, we are implicitly trimming
        // the right-side by specifying a limit to the stream's length.
        // An explicit right-side drop/trim/skip is not supported by the Stream API.
        var countAfterTrim = count - (countToDropFromEachSide * 2); // visada > 0? ir <= count?
        var bothSidesTrimmed = oneSideTrimmed.limit(countAfterTrim);

        return bothSidesTrimmed.collect(Collectors.toList());
    }

}
