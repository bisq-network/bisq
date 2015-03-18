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

import org.bitcoinj.core.Coin;

import java.io.Serializable;

public class BankTransferStartedMessage extends TradeMessage implements Serializable {
    private static final long serialVersionUID = -3479634129543632523L;

    public final byte[] offererSignature;
    public final Coin offererPayoutAmount;
    public final Coin takerPayoutAmount;
    public final String offererPayoutAddress;

    public BankTransferStartedMessage(String tradeId,
                                      byte[] offererSignature,
                                      Coin offererPayoutAmount,
                                      Coin takerPayoutAmount,
                                      String offererPayoutAddress) {
        this.tradeId = tradeId;
        this.offererSignature = offererSignature;
        this.offererPayoutAmount = offererPayoutAmount;
        this.takerPayoutAmount = takerPayoutAmount;
        this.offererPayoutAddress = offererPayoutAddress;
    }
}
