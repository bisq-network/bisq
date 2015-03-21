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

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.fiat.FiatAccount;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;

public class Taker implements Serializable {
    private static long serialVersionUID = -4041809885931756860L;

    // Those fields are set at constructor but not declared as final because constructor is not called in case model gets created from a persisted model
    // Declared transient as they will be provided in any case at construction time
    transient public FiatAccount fiatAccount;
    transient public String accountId;
    transient public PublicKey p2pSigPubKey;
    transient public PublicKey p2pEncryptPublicKey;
    transient public byte[] registrationPubKey; // TODO not read yet, missing impl.
    transient public DeterministicKey registrationKeyPair;
    transient public AddressEntry addressEntry;
    transient public byte[] tradeWalletPubKey;

    // written by tasks
    public List<TransactionOutput> connectedOutputsForAllInputs;
    public List<TransactionOutput> outputs;
    public Coin payoutAmount;
    public Transaction preparedDepositTx;

}
