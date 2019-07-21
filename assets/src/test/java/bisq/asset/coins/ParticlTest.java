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

public class ParticlTest extends AbstractAssetTest {

    public ParticlTest() {
        super(new Particl());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PZdYWHgyhuG7NHVCzEkkx3dcLKurTpvmo6");
        assertValidAddress("RJAPhgckEgRGVPZa9WoGSWW24spskSfLTQ");
        assertValidAddress("PaqMewoBY4vufTkKeSy91su3CNwviGg4EK");
        assertValidAddress("PpWHwrkUKRYvbZbTic57YZ1zjmsV9X9Wu7");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO");
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek");
    }
}
