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

package bisq.core.dao.state.model.governance;

import java.math.BigInteger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaoArithmeticsV2 {
    public static int addInteger(int a, int b) {
        return BigInteger.valueOf(a)
                .add(BigInteger.valueOf(b)).intValueExact();
    }

    public static long addLong(long a, long b) {
        return BigInteger.valueOf(a)
                .add(BigInteger.valueOf(b)).longValueExact();
    }

    public static long divideLong(long numerator, long denominator) {
        if (denominator == 0) {
            log.warn("denominator must not be zero. denominator={}", denominator);
            return 0;
        }
        return BigInteger.valueOf(numerator)
                .divide(BigInteger.valueOf(denominator))
                .longValueExact();
    }

    public static long multiplyLong(long a, long b) {

        return BigInteger.valueOf(a)
                .multiply(BigInteger.valueOf(b))
                .longValueExact();
    }

    public static long multiplyAndDivide(long a, long b, long denominator) {
        if (denominator == 0) {
            log.warn("denominator must not be zero. denominator={}", denominator);
            return 0;
        }
        return BigInteger.valueOf(a)
                .multiply(BigInteger.valueOf(b))
                .divide(BigInteger.valueOf(denominator))
                .longValueExact();
    }
}
