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

import bisq.asset.AltCoinAccountDisclaimer;
import bisq.asset.Coin;
import bisq.asset.RegexAddressValidator;

/**
 * Here is info from a Beam developer regarding validation.
 *
 * Well, unfortunately the range length is quite large. The BbsChannel is 64 bit = 8 bytes, the pubkey is 32 bytes.
 * So, the length may be up to 80 chars. The minimum length "theoretically" can also drop to a small length, if the
 * channel==0, and the pubkey starts with many zeroes  (very unlikely, but possible). So, besides being up to 80 chars
 * lower-case hex there's not much can be tested. A more robust test would also check if the pubkey is indeed valid,
 * but it's a more complex test, requiring cryptographic code.
 *
 */
@AltCoinAccountDisclaimer("account.altcoin.popup.beam.msg")
public class Beam extends Coin {
    public Beam() {
        super("Beam", "BEAM", new RegexAddressValidator("^([0-9a-f]{1,80})$"));
    }
}
