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

@AltCoinAccountDisclaimer("account.altcoin.popup.blk-burnt.msg")
public class BurntBlackCoin extends Coin {
    public static final short PAYLOAD_LIMIT = 15000;

    public BurntBlackCoin() {
        super("Burnt BlackCoin",
              "BLK-BURNT",
              new RegexAddressValidator(String.format("(?:[0-9a-z]{2}?){1,%d}+", 2 * PAYLOAD_LIMIT)));
    }
}
