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

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
public class RequestPublishDepositTxMessage extends TradeMessage implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;

    public final FiatAccount takerFiatAccount;
    public final String takerAccountId;
    public final PublicKey takerP2PSigPublicKey;
    public final PublicKey takerP2PEncryptPublicKey;
    public final String takerContractAsJson;
    public final String takerContractSignature;
    public final String takerPayoutAddressString;
    public final Transaction takersPreparedDepositTx;
    public final List<TransactionOutput> takerConnectedOutputsForAllInputs;

    public RequestPublishDepositTxMessage(String tradeId,
                                          FiatAccount takerFiatAccount,
                                          String takerAccountId,
                                          PublicKey takerP2PSigPublicKey,
                                          PublicKey takerP2PEncryptPublicKey,
                                          String takerContractAsJson,
                                          String takerContractSignature,
                                          String takerPayoutAddressString,
                                          Transaction takersPreparedDepositTx,
                                          List<TransactionOutput> takerConnectedOutputsForAllInputs) {
        super(tradeId);
        this.takerFiatAccount = takerFiatAccount;
        this.takerAccountId = takerAccountId;
        this.takerP2PSigPublicKey = takerP2PSigPublicKey;
        this.takerP2PEncryptPublicKey = takerP2PEncryptPublicKey;
        this.takerContractAsJson = takerContractAsJson;
        this.takerContractSignature = takerContractSignature;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.takersPreparedDepositTx = takersPreparedDepositTx;
        this.takerConnectedOutputsForAllInputs = takerConnectedOutputsForAllInputs;
    }
}
