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

package bisq.core.trade.protocol;

import bisq.core.btc.model.RawTransactionInput;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.ArrayList;

import lombok.Setter;

@Setter
public class TxData {
    public Transaction tx;
    public Coin bsqOutput;
    public Coin btcOutput;
    public Coin btcChange;
    public ArrayList<RawTransactionInput> bsqInputs;
    public ArrayList<RawTransactionInput> btcInputs;

    public TxData(Transaction tx,
                  Coin bsqOutput,
                  Coin btcOutput,
                  Coin btcChange,
                  ArrayList<RawTransactionInput> bsqInputs,
                  ArrayList<RawTransactionInput> btcInputs) {
        this.tx = tx;
        this.bsqOutput = bsqOutput;
        this.btcOutput = btcOutput;
        this.btcChange = btcChange;
        this.bsqInputs = bsqInputs;
        this.btcInputs = btcInputs;
    }
}
