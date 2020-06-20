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

package bisq.common.util;

import java.util.DoubleSummaryStatistics;

/* Adds logic to DoubleSummaryStatistics for keeping track of sum of squares
 * and computing population variance and population standard deviation.
 * Kahan summation algorithm (for `getSumOfSquares`) sourced from the DoubleSummaryStatistics class.
 * Incremental variance algorithm sourced from https://math.stackexchange.com/a/1379804/316756
 */
public class DoubleSummaryStatisticsWithStdDev extends DoubleSummaryStatistics {
    private double sumOfSquares;
    private double sumOfSquaresCompensation; // Low order bits of sum of squares
    private double simpleSumOfSquares; // Used to compute right sum of squares for non-finite inputs

    @Override
    public void accept(double value) {
        super.accept(value);
        double valueSquared = value * value;
        simpleSumOfSquares += valueSquared;
        sumOfSquaresWithCompensation(valueSquared);
    }

    public void combine(DoubleSummaryStatisticsWithStdDev other) {
        super.combine(other);
        simpleSumOfSquares += other.simpleSumOfSquares;
        sumOfSquaresWithCompensation(other.sumOfSquares);
        sumOfSquaresWithCompensation(other.sumOfSquaresCompensation);
    }

    /* Incorporate a new squared double value using Kahan summation /
     * compensated summation.
     */
    private void sumOfSquaresWithCompensation(double valueSquared) {
        double tmp = valueSquared - sumOfSquaresCompensation;
        double velvel = sumOfSquares + tmp; // Little wolf of rounding error
        sumOfSquaresCompensation = (velvel - sumOfSquares) - tmp;
        sumOfSquares = velvel;
    }

    private double getSumOfSquares() {
        // Better error bounds to add both terms as the final sum of squares
        double tmp = sumOfSquares + sumOfSquaresCompensation;
        if (Double.isNaN(tmp) && Double.isInfinite(simpleSumOfSquares))
            // If the compensated sum of squares is spuriously NaN from
            // accumulating one or more same-signed infinite values,
            // return the correctly-signed infinity stored in
            // simpleSumOfSquares.
            return simpleSumOfSquares;
        else
            return tmp;
    }

    private double getVariance() {
        double sumOfSquares = getSumOfSquares();
        long count = getCount();
        double mean = getAverage();
        return (sumOfSquares / count) - (mean * mean);
    }

    public final double getStandardDeviation() {
        double variance = getVariance();
        return Math.sqrt(variance);
    }

}
