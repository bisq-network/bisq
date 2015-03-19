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

import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.p2p.Peer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;

import java.util.List;

public class OffererModel implements Serializable {
    private static final long serialVersionUID = 1582902150121576205L;

    // Those fields are set at constructor but not declared as final because constructor is not called in case model gets created from a persisted model
    // Declared transient as they will be provided in any case at construction time
    public Peer peer;

    // written by tasks
    public byte[] pubKey;
    public Coin payoutAmount;
    public String payoutAddressString;
    public List<TransactionOutput> connectedOutputsForAllInputs;
    public List<TransactionOutput> outputs;
    public byte[] signature;
    public FiatAccount fiatAccount;
    public String accountId;

}
