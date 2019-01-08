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

public class WrkzCoinTest extends AbstractAssetTest {

    public WrkzCoinTest() {
        super(new WrkzCoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("WrkzjsomAAfH8kotfaTyVYfya7PNQt2oL4regF1VpTV9TSezdyxcQpRW2jGptwPP6zLgQUa7Lem1dBWfGM7LfJqs719UZhX9Hg");
        assertValidAddress("WrkzpRgV26G8p8FUfFzaYbd15Nmq3SsRSVbG8yPjvt4W4D5KBHTV2RHbzQVE1TAt1NV21Tp6xiFATJT8QXoxeEUQ8DPY1Zkjnf");
        assertValidAddress("WrkzmetNqgJG5SwtaVhyTxijdx6JGtUeHELTpwfgC9Ym1Ps4JdQtanXLK8Xk5TeMUTEbsmDJ8taXYiyYZpPHSg5X1wC8ij7fdG");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("WrkzQokcStLUSALE5Ra17v2n6ad65h8wL5vqABKkoWy7Xicz9znqPSgS2MRVkuYtRAaJiMFuyDCFF1oJgT7PHb8i9yM");
        assertInvalidAddress("WrkskixT63cYzLFmDoA5WN7RbihYBwbzJJmjR9zgjD3ZUotbFGBgv1RaUAu1fWWT4QeEEktQfZK9AFPh19t2U8uG49EH3WSVEn");
        assertInvalidAddress("");
        assertInvalidAddress("WrkzUAxg9TSdkh6tfh5pk84XgKeyNe8T4TvaSgk87kk6iCUEitkk2sk6wVtKJXk5BM3kwh2ftnkaVfBWfBPr8igZ2xkn#RoUxF");
        assertInvalidAddress("WrkzXTU4REbRijuLPpds2k4BhcBGgXFpeEaXKs49D7$PFuqBYpQw2tQbAApoQLAp2iWVsoxiPmcERXhHrhtCLnzL4ezB8kAbxH");
        assertInvalidAddress("cccd2bd37455350e7586cf9315c7f3acd3de56321aa356ff3391bd21f0bbf502");
    }
}
