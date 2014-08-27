/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.Transaction;

import org.junit.Test;

import static org.junit.Assert.*;

public class RestrictionsTest {
    @Test
    public void testIsMinSpendableAmount() {
        Coin amount = null;
        assertFalse("tx unfunded, pending", Restrictions.isMinSpendableAmount(amount));

        amount = Coin.ZERO;
        assertFalse("tx unfunded, pending", Restrictions.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE;
        assertFalse("tx unfunded, pending", Restrictions.isMinSpendableAmount(amount));

        amount = Transaction.MIN_NONDUST_OUTPUT;
        assertFalse("tx unfunded, pending", Restrictions.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT);
        assertFalse("tx unfunded, pending", Restrictions.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT).add(Coin.valueOf(1));
        assertTrue("tx unfunded, pending", Restrictions.isMinSpendableAmount(amount));
    }
}
