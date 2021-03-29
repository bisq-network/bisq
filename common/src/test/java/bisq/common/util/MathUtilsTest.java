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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MathUtilsTest {

    @Test(expected = IllegalArgumentException.class)
    public void testRoundDoubleWithInfiniteArg() {
        MathUtils.roundDouble(Double.POSITIVE_INFINITY, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRoundDoubleWithNaNArg() {
        MathUtils.roundDouble(Double.NaN, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRoundDoubleWithNegativePrecision() {
        MathUtils.roundDouble(3, -1);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testMovingAverageWithoutOutlierExclusion() {
        var values = new int[]{4, 5, 3, 1, 2, 4};
        // Moving average = 4, 4.5, 4, 3, 2, 7/3
        var movingAverage = new MathUtils.MovingAverage(3, 0);
        int i = 0;
        assertEquals(4, movingAverage.next(values[i++]).get(),0.001);
        assertEquals(4.5, movingAverage.next(values[i++]).get(),0.001);
        assertEquals(4, movingAverage.next(values[i++]).get(),0.001);
        assertEquals(3, movingAverage.next(values[i++]).get(),0.001);
        assertEquals(2, movingAverage.next(values[i++]).get(),0.001);
        assertEquals((double) 7 / 3, movingAverage.next(values[i]).get(),0.001);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testMovingAverageWithOutlierExclusion() {
        var values = new int[]{100, 102, 95, 101, 120, 115};
        // Moving average = N/A, N/A, 99, 99.333..., N/A, 103.666...
        var movingAverage = new MathUtils.MovingAverage(3, 0.2);
        int i = 0;
        assertFalse(movingAverage.next(values[i++]).isPresent());
        assertFalse(movingAverage.next(values[i++]).isPresent());
        assertEquals(99, movingAverage.next(values[i++]).get(),0.001);
        assertEquals(99.333, movingAverage.next(values[i++]).get(),0.001);
        assertFalse(movingAverage.next(values[i++]).isPresent());
        assertEquals(103.666, movingAverage.next(values[i]).get(),0.001);
    }
}
