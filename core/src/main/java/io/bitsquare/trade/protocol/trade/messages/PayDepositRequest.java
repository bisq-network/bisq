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

import io.bitsquare.app.Version;
import io.bitsquare.crypto.PubKeyRing;
import io.bitsquare.fiat.FiatAccount;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
public class PayDepositRequest extends TradeMessage implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final List<TransactionOutput> buyerConnectedOutputsForAllInputs;
    public final List<TransactionOutput> buyerOutputs;
    public final byte[] buyerTradeWalletPubKey;
    public final boolean isInitialRequest;
    public final PubKeyRing buyerPubKeyRing;
    public final FiatAccount buyerFiatAccount;
    public final String buyerAccountId;
    public final Coin tradeAmount;

    public PayDepositRequest(String tradeId,
                             Coin tradeAmount,
                             boolean isInitialRequest,
                             List<TransactionOutput> buyerConnectedOutputsForAllInputs,
                             List<TransactionOutput> buyerOutputs,
                             byte[] buyerTradeWalletPubKey,
                             PubKeyRing buyerPubKeyRing,
                             FiatAccount buyerFiatAccount,
                             String buyerAccountId) {
        super(tradeId);
        this.tradeAmount = tradeAmount;
        this.isInitialRequest = isInitialRequest;
        this.buyerPubKeyRing = buyerPubKeyRing;
        this.buyerConnectedOutputsForAllInputs = buyerConnectedOutputsForAllInputs;
        this.buyerOutputs = buyerOutputs;
        this.buyerTradeWalletPubKey = buyerTradeWalletPubKey;
        this.buyerFiatAccount = buyerFiatAccount;
        this.buyerAccountId = buyerAccountId;
    }
}
