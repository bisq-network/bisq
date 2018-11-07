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

package bisq.core.btc.exceptions;

import org.bitcoinj.core.Transaction;

import lombok.Getter;

import javax.annotation.Nullable;


public class TxMalleabilityException extends TxBroadcastException {
    @Getter
    @Nullable
    private final Transaction localTx;
    @Getter
    @Nullable
    private final Transaction networkTx;

    public TxMalleabilityException(Transaction localTx, Transaction networkTx) {
        super("The transaction we received from the Bitcoin network has a different txId as the one we broadcasted.\n" +
                "txId of local tx=" + localTx.getHashAsString() +
                ", txId of received tx=" + localTx.getHashAsString());
        this.localTx = localTx;
        this.networkTx = networkTx;
    }

    @Override
    public String toString() {
        return "TxMalleabilityException{" +
                "\n     localTx=" + localTx +
                ",\n     networkTx=" + networkTx +
                "\n} " + super.toString();
    }
}
