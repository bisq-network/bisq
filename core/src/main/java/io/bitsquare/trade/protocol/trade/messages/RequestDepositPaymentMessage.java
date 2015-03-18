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

import java.util.List;

public class RequestDepositPaymentMessage extends TradeMessage implements Serializable {
    private static final long serialVersionUID = -3988720410493712913L;

    public final List<TransactionOutput> offererConnectedOutputsForAllInputs;
    public final List<TransactionOutput> offererOutputs;
    public final byte[] offererPubKey;
    public final FiatAccount offererFiatAccount;
    public final String offererAccountId;

    public RequestDepositPaymentMessage(String tradeId,
                                        List<TransactionOutput> offererConnectedOutputsForAllInputs,
                                        List<TransactionOutput> offererOutputs,
                                        byte[] offererPubKey,
                                        FiatAccount offererFiatAccount,
                                        String offererAccountId) {
        this.tradeId = tradeId;
        this.offererConnectedOutputsForAllInputs = offererConnectedOutputsForAllInputs;
        this.offererOutputs = offererOutputs;
        this.offererPubKey = offererPubKey;
        this.offererFiatAccount = offererFiatAccount;
        this.offererAccountId = offererAccountId;
    }
}
