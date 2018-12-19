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

public class SUNPTest extends AbstractAssetTest {

    public SUNPTest() {
        super(new SUNP());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("SQEasE1ErGeSSqjruzL879UNPeV7sWbnAN");
        assertValidAddress("SRK1eahAugt3k6BQjbR1EFYWTAGzg6uXPd");
        assertValidAddress("SfCe8XrgG2FTPLyuheJ2kMVTD4QehjPUPb");
        assertValidAddress("SkSJUTCgfJyGM6REhvt54JNQswnwsGHt5E");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("sQEasE1ErGeSSqjruzL879UNPeV7sWbnAN");
        assertInvalidAddress("SRK1eahBugt3k6BQjbR1EFYWTAGzg6uXpD");
        assertInvalidAddress("SfCe8XrgG2FTPLyuheJ2kMVTD4QehjLULd");
        assertInvalidAddress("SSSJUTCgfJyGM6REhvt54JNQswnwsGHt6e");
        assertInvalidAddress("SfCe8XrgG2");
    }
}
