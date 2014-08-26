/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.funds.deposit;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.funds.withdrawal.WithdrawalListItem;

public class DepositListItem extends WithdrawalListItem {
    public DepositListItem(AddressEntry addressEntry, WalletFacade walletFacade) {
        super(addressEntry, walletFacade);
    }

}
