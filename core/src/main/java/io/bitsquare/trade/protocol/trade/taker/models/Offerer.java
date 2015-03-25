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

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;

public class Offerer implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    // written by tasks
    public byte[] tradeWalletPubKey;
    public Coin payoutAmount;
    public String payoutAddressString;
    public List<TransactionOutput> connectedOutputsForAllInputs;
    public List<TransactionOutput> outputs;
    public byte[] signature;
    public FiatAccount fiatAccount;
    public String accountId;
    public PublicKey p2pSigPublicKey;
    public PublicKey p2pEncryptPubKey;

}
