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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class QMCoinTest extends AbstractAssetTest {

    public QMCoinTest() {
        super(new QMCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("QSXwS2opau1PYsvj4PrirPkP6LQHeKbQDx");
        assertValidAddress("QbvD8CPJwAmpQoE8CQhzcfWp1EAmT2E298");
        assertValidAddress("QUAzsb7nqp7XVsRy9vjaE4kTUpgP1pFeoL");
        assertValidAddress("QQDvVM2s3WYa8EZQS1s2esRkR4zmrjy94d");
        assertValidAddress("QgdkWtsy1inr9j8RUrqDeVnrJmhE28WnLX");
        assertValidAddress("Qii56aanBMiEPpjHoaE4zgEW4jPuhGjuj5");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("qSXwS2opau1PYsvj4PrirPkP6LQHeKbQDx");
        assertInvalidAddress("QbvD8CPJwAmpQoE8CQhzcfWp1EAmT2E2989");
        assertInvalidAddress("QUAzsb7nq07XVsRy9vjaE4kTUpgP1pFeoL");
        assertInvalidAddress("QQDvVM2s3WYa8EZQS1s2OsRkR4zmrjy94d");
        assertInvalidAddress("QgdkWtsy1inr9j8RUrqDIVnrJmhE28WnLX");
        assertInvalidAddress("Qii56aanBMiEPpjHoaE4lgEW4jPuhGjuj5");
    }
}
