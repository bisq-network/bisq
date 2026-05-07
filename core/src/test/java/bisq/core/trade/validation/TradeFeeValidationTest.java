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

package bisq.core.trade.validation;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.TradeValidationTestUtils.configureTradeFeeService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TradeFeeValidationTest {
    @Test
    void checkTakerFeeAcceptsExpectedFees() {
        Coin takerFee = Coin.valueOf(100);

        assertSame(takerFee, TradeFeeValidation.checkTakerFee(takerFee, Coin.valueOf(100)));
        assertEquals(100, TradeFeeValidation.checkTakerFeeInTolerance(100, 100));
        assertEquals(150, TradeFeeValidation.checkTakerFeeInTolerance(150, 100));
        assertEquals(67, TradeFeeValidation.checkTakerFeeInTolerance(67, 100));
    }

    @Test
    void checkTakerFeeAcceptsCalculatedExpectedFees() {
        configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100));
        Coin takerFee = Coin.valueOf(100);

        assertSame(takerFee, TradeFeeValidation.checkTakerFee(takerFee, true, Coin.valueOf(3_000)));
        assertEquals(100, TradeFeeValidation.checkTakerFee(100, true, Coin.valueOf(3_000)));
    }

    @Test
    void checkFeeMatchesExpectedRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkTakerFee(Coin.ZERO, Coin.valueOf(100)));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkTakerFee(Coin.valueOf(-1), Coin.valueOf(100)));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkTakerFee(Coin.valueOf(100), Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkTakerFee(Coin.valueOf(100), Coin.valueOf(-1)));
    }

    @Test
    void checkTakerFeeRejectsUnexpectedFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkTakerFeeInTolerance(151, 100));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkTakerFeeInTolerance(49, 100));
    }

    @Test
    void checkTakerFeeRejectsNullFees() {
        assertThrows(NullPointerException.class, () -> TradeFeeValidation.checkTakerFee(null, Coin.valueOf(100)));
        assertThrows(NullPointerException.class, () -> TradeFeeValidation.checkTakerFee(Coin.valueOf(100), null));
    }

    @Test
    void checkMakerFeeAcceptsExpectedFees() {
        Coin makerFee = Coin.valueOf(77);

        assertSame(makerFee, TradeFeeValidation.checkMakerFee(makerFee, Coin.valueOf(77)));
        assertEquals(77, TradeFeeValidation.checkMakerFeeInTolerance(77, 77));
        assertEquals(154, TradeFeeValidation.checkMakerFeeInTolerance(154, 77));
        assertEquals(39, TradeFeeValidation.checkMakerFeeInTolerance(39, 77));
    }

    @Test
    void checkMakerFeeAcceptsCalculatedExpectedFees() {
        configureTradeFeeService(Coin.valueOf(77), Coin.valueOf(100));
        Coin makerFee = Coin.valueOf(77);

        assertSame(makerFee, TradeFeeValidation.checkMakerFee(makerFee, false, Coin.valueOf(3_000)));
        assertEquals(77, TradeFeeValidation.checkMakerFee(77, false, Coin.valueOf(3_000)));
    }

    @Test
    void checkMakerFeeRejectsZeroAndNegativeFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkMakerFee(Coin.ZERO, Coin.valueOf(77)));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkMakerFee(Coin.valueOf(-1), Coin.valueOf(77)));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkMakerFee(Coin.valueOf(77), Coin.ZERO));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkMakerFee(Coin.valueOf(77), Coin.valueOf(-1)));
    }

    @Test
    void checkMakerFeeRejectsUnexpectedFees() {
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkMakerFee(Coin.valueOf(155), Coin.valueOf(77)));
        assertThrows(IllegalArgumentException.class, () -> TradeFeeValidation.checkMakerFeeInTolerance(155, 77));
    }

    @Test
    void checkMakerFeeRejectsNullFees() {
        assertThrows(NullPointerException.class, () -> TradeFeeValidation.checkMakerFee(null, Coin.valueOf(77)));
        assertThrows(NullPointerException.class, () -> TradeFeeValidation.checkMakerFee(Coin.valueOf(77), null));
    }
}
