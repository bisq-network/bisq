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

public class FuturoCoinTest extends AbstractAssetTest {

    public FuturoCoinTest() {
        super(new FuturoCoin());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("FkWEKpguPvG3CbaLpg46Fka4ZCCh2zuUCZ");
        assertValidAddress("FZ6UhtneD6AciLywYWJSwmN3XgdoDGEjjF");
        assertValidAddress("FYjkqH1XVp4oF5PvFK5JJdC1Mb2eZAbGDk");
        assertValidAddress("FheRu8mY47PpUCx4kvjgsRQcLtoG9uDbT8");
        assertValidAddress("FYdmbRBJ3LBRATvj8E8CwjW7v1EkngTCDa");
        assertValidAddress("FaKWCQ662qG3nmy6bjWV5a4Mse6wfMFde6");
        assertValidAddress("FcXEfdEPj7BLnSGUjBTHFZomxEwWAo9nyv");
        assertValidAddress("Fd7gZ7dNJ1toY6TeWy3rf2dUvyRudggTLv");
        assertValidAddress("FbRXmJUDgf5URuVGyM223P8R2JArXSSm6u");
    }

    @Override
    public void testInvalidAddresses() {
        assertInvalidAddress("FkWEKpguPvG3CbaLpg46Fka4ZCCh2zuUC2");
        assertInvalidAddress("FZ6UhtneD6AciLywYWJSwmN3XgdoDGEjjFdd");
        assertInvalidAddress("FYjkqH1XVp4oF5PvFK5JJdC1Mb2eZAb");
        assertInvalidAddress("FheRu8mY47PpUCx4kvjgsRQcLtoG9uDbT9");
        assertInvalidAddress("Fd7gZ7dNJ1toY6TeWy3rf2dUvyRudggTL");
        assertInvalidAddress("FbRXmJUDgf5URuVGyM223P8R2JArXSSm61");
    }
}
