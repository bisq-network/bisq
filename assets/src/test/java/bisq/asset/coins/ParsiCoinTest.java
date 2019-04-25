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

public class ParsiCoinTest extends AbstractAssetTest {

    public ParsiCoinTest() {
        super(new ParsiCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PARSGnjdcRG4gY9g4rMTFAEHZLGU7uK8YMiFY3Do1uzoMz4LMA6PqmdPp7ZxDu25b56RyhCevkWjbAMng532iFFj8L5RaPyT4s");
        assertValidAddress("PARSftfY5pwJaUFtaxThVgKY9Sepd4mG44WpyncbtAxTddwTvJ84GCgGfoxYjzG53kLhRm21ENWp3fx5bneArq1D815ZoWNVqA");
        assertValidAddress("PARSju1hCQ5GmXSRbca8weGYDn2pqCypgLyTrENqL4XU3mdEx1mZ2vR7osrVA2hHNGRJRA5pRENF2Q8Pee8BscHoABVrcfkWnx");
    }

    @Test
    public void testInvalidAddresses() {
		assertInvalidAddress("");
		assertInvalidAddress("1GfqxEuuFmwwHTFkch3Aq3frEBbdpYfWPP");
        assertInvalidAddress("PARsaUEu1c9HWPQx6WpCcjZNmpS3vMhN4Jws12KrccLhH9vzUw4racG3g7St2FKDYngjcnkNF3N2sKQJ5jv1NYqD2buCpmVKE");
        assertInvalidAddress("PArSeoCiQL2Rjyo9GR39boeLCTM6ou3zGiv8AuFFblGHfNasy5iKfvG6JgnksNby26J6i5sEorRcmG8gF2AxC8bYiHyDGEfD6hp8T9KfwjQxVa");
        assertInvalidAddress("PaRSaUEu1c9HWPQx6WpCcjZNmpS3vMhN4Jws12rccLhH9vzUw4racG3g7St2#FKDYngjcnkNF3N2sKQJ5jv1NYqD2buCpmVKE");
        assertInvalidAddress("pARSeoCiQL2Rjyo9GR39boeLCTM6ou3zGiv8AuFFby5iKfvG6JNby26J6i5s$&*orRcmG8gF2AxC8bYiHyDGEfD6hp8T9KfwjQxVa");
        assertInvalidAddress("hyrjMmPhaznQkJD6C9dcmbBH9y6r9vYAg2aTG9CHSzL1R89xrFi7wj1azmkXyLPiWDBeTCsKGMmr6JzygbP2ZGSN2JqWs1WcK");
        assertInvalidAddress("parsGnjdcRG4gY9g4rMTFAEHZLGU7uK8YMiFY3Do1uzoMz4LMA6PqmdPp7ZxDu25b56RyhCevkWjbAMng532iFFj8L5RaPyT");
    }
}