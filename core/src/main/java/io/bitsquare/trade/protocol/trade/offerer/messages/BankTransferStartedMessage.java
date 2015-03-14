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

package io.bitsquare.trade.protocol.trade.offerer.messages;

import io.bitsquare.trade.protocol.trade.TradeMessage;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.io.Serializable;

public class BankTransferStartedMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = -3479634129543632523L;
    private final String tradeId;

    private final Transaction depositTx;
    private final byte[] offererSignature;
    private final Coin offererPaybackAmount;
    private final Coin takerPaybackAmount;
    private final String offererPayoutAddress;

    public BankTransferStartedMessage(String tradeId,
                                      Transaction depositTx,
                                      byte[] offererSignature,
                                      Coin offererPaybackAmount,
                                      Coin takerPaybackAmount,
                                      String offererPayoutAddress) {
        this.tradeId = tradeId;
        this.depositTx = depositTx;
        this.offererSignature = offererSignature;
        this.offererPaybackAmount = offererPaybackAmount;
        this.takerPaybackAmount = takerPaybackAmount;
        this.offererPayoutAddress = offererPayoutAddress;
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    public Transaction getDepositTx() {
        return depositTx;
    }

    public String getOffererPayoutAddress() {
        return offererPayoutAddress;
    }

    public Coin getOffererPaybackAmount() {
        return offererPaybackAmount;
    }

    public Coin getTakerPaybackAmount() {
        return takerPaybackAmount;
    }

    public byte[] getOffererSignature() {
        return offererSignature;
    }
}
