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

public class AmitycoinTest extends AbstractAssetTest {

    public AmitycoinTest() {
        super(new Amitycoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("amitMgDfvfUZ2CP1g1SEJQSN4n7qK4d45hqXSDtiFMwE5uo7DnSihknJzcEG9WtFc26fnhDHK6ydjBDKe6wjCoGt4RiP18a5Zb");
        assertValidAddress("amitUnFFwApLG9btiPWRgTjRCQUj9kZjQJ8kH3ZraSsCU4yzX4AzgaoP8jkgXhp5c5jQT3idFJChAPYzA2EydJ5A4bShqrEixa");
        assertValidAddress("amitAcVJTUZKJtYYsosMXJBQeEbt3ZV9qSvoQ1EqkvA45MRUaYWECYNKyRZ82BvLM9MPD2Gpud3DbGzGsStKnZ9x5yKVPVGJUa");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("amitAcVJTUZKJtYYsosMXJBQeEbt3ZV9qSvoQ1EqkvA45MRUaYWECYNKyRZ82BvLM9MPD2Gpud3DbGzGsStKnZ9");
        assertInvalidAddress("amitAcVJTUZKJtYYsosMXJBQeEbt3ZV9qSvoQ1EqkvA45MRUaYWECYNKyRZ82BvLM9MPD2Gpud3DbGzGsStKnZ9x5yKVPVGJUaljashfeafh");
        assertInvalidAddress("");
        assertInvalidAddress("amitAcVJTUZKJtYYsosMXJBQeEbt3ZV9qSvoQ1EqkvA45MRUaYWECY#RoPOWRwpsx1F");
        assertInvalidAddress("amitAcVJTUZKJtYYsosMXJByRZ82BvLM9MPD2Gpud3DbGzGsStKnZ9x5yKVPVGJUaJbc2q4C4fWN$C4fWNLoDLDvADvpjNYdt3sdRB434UidKXimQQn");
        assertInvalidAddress("dsfkjasd56yaSDdguaw");
        assertInvalidAddress("KEKlulzlksadfwe");
        assertInvalidAddress("HOleeSheetdsdjqwqwpoo3");
    }
}  
