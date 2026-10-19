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
import org.bitcoinj.core.Transaction;

import lombok.Getter;

/**
 * The amounts of a withdrawal, taken from its fee estimation transaction, so that the amounts shown are the amounts
 * sent. A remainder of the inputs that cannot be returned as change is part of the fee: bitcoinj adds a small one to
 * the fee of the transaction itself, and a change output that bitcoinj kept below our dust limit is added here.
 */
@Getter
public class WithdrawalEstimate {
    private final Transaction transaction;
    // The first output of the transaction
    private final Coin receiverAmount;
    // A change output of the transaction below our dust limit
    private final Coin dust;
    private final Coin fee;
    // What the selected addresses spend: the receiver's amount and the fee
    private final Coin sendersAmount;

    public WithdrawalEstimate(Transaction transaction, Coin dust) {
        this.transaction = transaction;
        this.dust = dust;
        receiverAmount = transaction.getOutput(0).getValue();
        fee = transaction.getFee().add(dust);
        sendersAmount = receiverAmount.add(fee);
    }
}
