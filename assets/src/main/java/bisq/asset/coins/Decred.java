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

import bisq.asset.Coin;
import bisq.asset.I18n;
import bisq.asset.RegexAddressValidator;

public class Decred extends Coin {

	public Decred() {
		super("Decred", "DCR", new DcrAddressValidator());
    }
    
    public static class DcrAddressValidator extends RegexAddressValidator {

        public DcrAddressValidator() {
            super("^[Dk|Ds|De|DS|Dc|Pm][a-zA-Z0-9]{24,34}", I18n.DISPLAY_STRINGS.getString("account.altcoin.popup.validation.DCR"));
        }
    }
}
