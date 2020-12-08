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

import bisq.core.util.InlierUtil;

import bisq.common.util.Tuple2;

import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

public class AxisInlierUtils {

    /* Returns a ListChangeListener that is meant to be attached to an
     * ObservableList. On event, it triggers a recalculation of a provided
     * axis' range so as to zoom in on inliers.
     */
    public static ListChangeListener<XYChart.Data<Number, Number>> getListenerThatZoomsToInliers(
            NumberAxis axis,
            int maxNumberOfTicks,
            double percentToTrim,
            double howManyStdDevsConstituteOutlier
    ) {
        return change -> {
            boolean axisHasBeenInitialized = axis != null;
            if (axisHasBeenInitialized) {
                zoomToInliers(
                        axis,
                        change.getList(),
                        maxNumberOfTicks,
                        percentToTrim,
                        howManyStdDevsConstituteOutlier
                );
            }
        };
    }

    /* Applies the inlier range to the axis bounds and sets an appropriate tick-unit.
     * The methods describing the arguments passed here are `computeReferenceTickUnit`,
     * `trim`, and `computeInlierThreshold`.
     */
    public static void zoomToInliers(
            NumberAxis yAxis,
            ObservableList<? extends XYChart.Data<Number, Number>> xyValues,
            int maxNumberOfTicks,
            double percentToTrim,
            double howManyStdDevsConstituteOutlier
    ) {
        List<Double> yValues = extractYValues(xyValues);

        if (yValues.size() < 3) {
            // with less than 3 elements, there is no meaningful inlier analysis
            return;
        }

        Tuple2<Double, Double> inlierRange =
                InlierUtil.findInlierRange(yValues, percentToTrim, howManyStdDevsConstituteOutlier);

        applyRange(yAxis, maxNumberOfTicks, inlierRange);
    }

    private static List<Double> extractYValues(ObservableList<? extends XYChart.Data<Number, Number>> xyValues) {
        return xyValues
                .stream()
                .map(xyData -> (double) xyData.getYValue())
                .collect(Collectors.toList());
    }

    /* On the given axis, sets the provided lower and upper bounds, and
     * computes an appropriate major tick unit (distance between major ticks in data-space).
     * External computation of tick unit is necessary, because JavaFX doesn't support automatic
     * tick unit computation when axis bounds are set manually.
     */
    private static void applyRange(NumberAxis axis, int maxNumberOfTicks, Tuple2<Double, Double> bounds) {
        var boundsWidth = getBoundsWidth(bounds);
        if (boundsWidth < 0) {
            throw new IllegalArgumentException(
                    "The lower bound must be a smaller number than the upper bound");
        }
        if (boundsWidth == 0 || Double.isNaN(boundsWidth)) {
            // less than 2 unique data-points: recalculating axis range doesn't make sense
            return;
        }

        axis.setAutoRanging(false);

        var lowerBound = bounds.first;
        var upperBound = bounds.second;

        // If one of the ends of the range weren't zero,
        // additional logic would be needed to make ticks "round".
        // Of course, many, if not most, charts benefit from having 0 on the axis.
        if (lowerBound > 0) {
            lowerBound = 0d;
        } else if (upperBound < 0) {
            upperBound = 0d;
        }

        axis.setLowerBound(lowerBound);
        axis.setUpperBound(upperBound);

        var referenceTickUnit = computeReferenceTickUnit(maxNumberOfTicks, bounds);

        var tickUnit = computeTickUnit(referenceTickUnit);

        axis.setTickUnit(tickUnit);
    }

    /* Uses bounds and maximum number of major ticks to find a reference tick unit
     * for the `computeTickUnit` method. The reference tick unit is later used as a
     * starting point for tick unit's search.
     * The rationale behind dividing the range/domain/width of an axis by maximum number
     * of ticks is that it yields a good number of ticks, but they are not "well rounded",
     * hence the next step of computing the actual tick unit.
     * `maxNumberOfTicks` specifies how many subdivisions (major tick units) an axis
     * should have at most. The final number of subdivisions, after `computeTickUnit`,
     * usually will be lower, but never higher.
     */
    private static double computeReferenceTickUnit(int maxNumberOfTicks, Tuple2<Double, Double> bounds) {
        if (maxNumberOfTicks <= 0) {
            throw new IllegalArgumentException("maxNumberOfTicks must be a positive number");
        }
        var width = getBoundsWidth(bounds);
        return width / maxNumberOfTicks;
    }

    /* Extracted from cern.extjfx.chart.DefaultTickUnitSupplier (licensed Apache 2.0).
     * Original description below; note that the `multipliers` vector is hardcoded in the method to the default value
     * used in the source class:
     *
     * Computes tick unit using the following formula: tickUnit = M*10^E, where M is one of the multipliers specified in
     * the constructor and E is an exponent of 10. Both M and E are selected so that the calculated unit is the smallest
     * (closest to the zero) value that is greater than or equal to the reference tick unit.
     *
     * For example with multipliers [1, 2, 5], the method will give the following results:
     *
     * computeTickUnit(0.01) returns 0.01
     * computeTickUnit(0.42) returns 0.5
     * computeTickUnit(1.73) returns 2
     * computeTickUnit(5)    returns 5
     * computeTickUnit(27)   returns 50
     *
     * @param referenceTickUnit the reference tick unit, must be a positive number
     */
    private static double computeTickUnit(double referenceTickUnit) {
        if (referenceTickUnit <= 0) {
            throw new IllegalArgumentException("The reference tick unit must be a positive number");
        }

        // Default multipliers vector extracted from the source class.
        double[] multipliers = {1d, 2.5, 5d};

        int BASE = 10;
        int exp = (int) Math.floor(Math.log10(referenceTickUnit));
        double factor = referenceTickUnit / Math.pow(BASE, exp);

        double multiplier = 0;
        int lastIndex = multipliers.length - 1;
        if (factor > multipliers[lastIndex]) {
            exp++;
            multiplier = multipliers[0];
        } else {
            for (int i = lastIndex; i >= 0; i--) {
                if (factor <= multipliers[i]) {
                    multiplier = multipliers[i];
                } else {
                    break;
                }
            }
        }
        return multiplier * Math.pow(BASE, exp);
    }

    private static double getBoundsWidth(Tuple2<Double, Double> bounds) {
        var lowerBound = bounds.first;
        var upperBound = bounds.second;
        return Math.abs(upperBound - lowerBound);
    }
}
