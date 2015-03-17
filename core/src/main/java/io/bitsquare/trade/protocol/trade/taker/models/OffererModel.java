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

package io.bitsquare.trade.protocol.trade.taker.models;

import io.bitsquare.network.Peer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.TransactionOutput;

import java.util.List;

public class OffererModel {
    public OffererModel(Peer peer) {
        this.peer = peer;
    }

    public final Peer peer;
    public byte[] pubKey;
    public Coin payoutAmount;
    public String payoutAddress;
    public List<TransactionOutput> connectedOutputsForAllInputs;
    public List<TransactionOutput> outputs;
    public ECKey.ECDSASignature signature;

}
