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


public class DaoArithmetics {
    // Consensus activation height for DAO related arithmetic hardening. Historical heights must keep
    // legacy primitive overflow behavior so replayed DAO state hashes remain deterministic.
    public static final int DAO_CONSENSUS_V2_ACTIVATION_HEIGHT = 954_200;

    public static int addInteger(int a, int b, long chainHeight) {
        if (isV2Activated(chainHeight)) {
            return DaoArithmeticsV2.addInteger(a, b);
        } else {
            return DaoArithmeticsLegacy.addInteger(a, b);
        }
    }

    public static long addLong(long a, long b, long chainHeight) {
        if (isV2Activated(chainHeight)) {
            return DaoArithmeticsV2.addLong(a, b);
        } else {
            return DaoArithmeticsLegacy.addLong(a, b);
        }
    }

    public static long divideLong(long numerator, long denominator, long chainHeight) {
        if (isV2Activated(chainHeight)) {
            return DaoArithmeticsV2.divideLong(numerator, denominator);
        } else {
            return DaoArithmeticsLegacy.divideLong(numerator, denominator);
        }
    }

    public static long multiplyLong(long a, long b, long chainHeight) {
        if (isV2Activated(chainHeight)) {
            return DaoArithmeticsV2.multiplyLong(a, b);
        } else {
            return DaoArithmeticsLegacy.multiplyLong(a, b);
        }
    }

    public static long multiplyAndDivide(long a, long b, long denominator, long chainHeight) {
        if (isV2Activated(chainHeight)) {
            return DaoArithmeticsV2.multiplyAndDivide(a, b, denominator);
        } else {
            return DaoArithmeticsLegacy.multiplyAndDivide(a, b, denominator);
        }
    }

    private static boolean isV2Activated(long chainHeight) {
        return chainHeight >= DAO_CONSENSUS_V2_ACTIVATION_HEIGHT;
    }
}
