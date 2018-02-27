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

package io.bisq.core.btc.wallet;

import org.bitcoinj.core.Coin;

public class ChangeBelowDustException extends Exception {
    public final Coin change;

    public ChangeBelowDustException(Coin change) {
        this(change, "Change value would be below min. dust. Change: " + change + " satoshis");
    }

    public ChangeBelowDustException(Coin change, String message) {
        super(message);
        this.change = change;
    }

}
