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

package bisq.core.btc.wallet;

import org.bitcoinj.core.Coin;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("ConstantConditions")
public class RestrictionsTest {
    @Test
    public void testIsMinSpendableAmount() {
        Coin amount = null;
        Coin txFee = Coin.valueOf(20000);

        amount = Coin.ZERO;
        assertFalse(Restrictions.isAboveDust(amount.subtract(txFee)));

        amount = txFee;
        assertFalse(Restrictions.isAboveDust(amount.subtract(txFee)));

        amount = Restrictions.getMinNonDustOutput();
        assertFalse(Restrictions.isAboveDust(amount.subtract(txFee)));

        amount = txFee.add(Restrictions.getMinNonDustOutput());
        assertTrue(Restrictions.isAboveDust(amount.subtract(txFee)));

        amount = txFee.add(Restrictions.getMinNonDustOutput()).add(Coin.valueOf(1));
        assertTrue(Restrictions.isAboveDust(amount.subtract(txFee)));
    }
}
