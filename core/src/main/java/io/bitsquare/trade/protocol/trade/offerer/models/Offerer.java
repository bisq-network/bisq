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

package io.bitsquare.trade.protocol.trade.offerer.models;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.fiat.FiatAccount;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;

public class Offerer implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    // Those fields are set at constructor but not declared as final because constructor is not called in case model gets created from a persisted model
    // Declared transient as they will be provided in any case at construction time
    transient public FiatAccount fiatAccount;
    transient public String accountId;
    transient public PublicKey p2pSigPubKey;
    transient public PublicKey p2pEncryptPubKey;
    transient public byte[] registrationPubKey;
    transient public DeterministicKey registrationKeyPair;
    transient public AddressEntry addressEntry;
    transient public byte[] tradeWalletPubKey;

    // written by tasks
    public byte[] payoutTxSignature;
    public Coin payoutAmount;
    public List<TransactionOutput> connectedOutputsForAllInputs;
    public List<TransactionOutput> outputs; // used to verify amounts with change outputs
}
