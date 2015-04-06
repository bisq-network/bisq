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

import io.bitsquare.p2p.MailboxMessage;

import org.bitcoinj.core.Transaction;

import java.io.Serializable;

import javax.annotation.concurrent.Immutable;

@Immutable
public class PayoutTxFinalizedMessage extends TradeMessage implements MailboxMessage, Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;

    public final Transaction payoutTx;

    public PayoutTxFinalizedMessage(String tradeId, Transaction payoutTx) {
        super(tradeId);
        this.payoutTx = payoutTx;
    }

}
