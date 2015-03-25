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

package io.bitsquare.trade.protocol.trade.messages;

import io.bitsquare.fiat.FiatAccount;

import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
public class RequestTakerDepositPaymentMessage extends TradeMessage implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;

    public final List<TransactionOutput> offererConnectedOutputsForAllInputs;
    public final List<TransactionOutput> offererOutputs;
    public final byte[] offererTradeWalletPubKey;
    public final PublicKey offererP2PSigPublicKey;
    public final PublicKey offererP2PEncryptPublicKey;
    public final FiatAccount offererFiatAccount;
    public final String offererAccountId;

    public RequestTakerDepositPaymentMessage(String tradeId,
                                             List<TransactionOutput> offererConnectedOutputsForAllInputs,
                                             List<TransactionOutput> offererOutputs,
                                             byte[] offererTradeWalletPubKey,
                                             PublicKey offererP2PSigPublicKey,
                                             PublicKey offererP2PEncryptPublicKey,
                                             FiatAccount offererFiatAccount,
                                             String offererAccountId) {
        super(tradeId);
        this.offererP2PSigPublicKey = offererP2PSigPublicKey;
        this.offererP2PEncryptPublicKey = offererP2PEncryptPublicKey;
        this.offererConnectedOutputsForAllInputs = offererConnectedOutputsForAllInputs;
        this.offererOutputs = offererOutputs;
        this.offererTradeWalletPubKey = offererTradeWalletPubKey;
        this.offererFiatAccount = offererFiatAccount;
        this.offererAccountId = offererAccountId;
    }
}
