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

package bisq.core.btc.model;

import org.bitcoinj.core.Coin;

/**
 * The amounts of the transaction that a withdrawal sends. Sending the sender's amount with the fee creates this
 * transaction again as long as the wallet does not change.
 *
 * @param receiverAmount the output to the receiver
 * @param fee            the fee of the transaction
 * @param vsize          the virtual size of the transaction
 * @param dust           the part of the fee that is a remainder of the selected coins, which a change output below
 *                       the dust limit would have returned
 */
public record WithdrawalEstimate(Coin receiverAmount, Coin fee, int vsize, Coin dust) {
    // What the selected addresses spend
    public Coin sendersAmount() {
        return receiverAmount.add(fee);
    }
}
