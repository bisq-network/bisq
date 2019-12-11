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

public class LimitlessVIPTest extends AbstractAssetTest {

    public LimitlessVIPTest() {
        super(new LimitlessVIP());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("TPfCkKQPK1FURXoAbvgEZUGK7pabqjwqPS");
        assertValidAddress("TQppRgMV3heoqpV3qVnercsvk4vKvQqutf");
        assertValidAddress("TPZDAaACPtKkBAEf2EsKJLMPiDML2APWoM");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1TM993FEHjreKmfmeVfrU7XNP8gJqRX5uhb");
        assertInvalidAddress("TM993FEHjreKmfmeVfrU7XNP8gJqRX5uhbd");
        assertInvalidAddress("TM993FEHjreKmfmeVfrU7XNP8gJqRX5uhb#");
    }
}
