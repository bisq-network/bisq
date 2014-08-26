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

package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.Coin;
import io.bitsquare.trade.protocol.TradeMessage;

import java.io.Serializable;

public class BankTransferInitedMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = -3479634129543632523L;
    private final String tradeId;

    private String depositTxAsHex;
    private String offererSignatureR;
    private String offererSignatureS;
    private Coin offererPaybackAmount;
    private Coin takerPaybackAmount;
    private String offererPayoutAddress;

    public BankTransferInitedMessage(String tradeId,
                                     String depositTxAsHex,
                                     String offererSignatureR,
                                     String offererSignatureS,
                                     Coin offererPaybackAmount,
                                     Coin takerPaybackAmount,
                                     String offererPayoutAddress) {
        this.tradeId = tradeId;
        this.depositTxAsHex = depositTxAsHex;
        this.offererSignatureR = offererSignatureR;
        this.offererSignatureS = offererSignatureS;
        this.offererPaybackAmount = offererPaybackAmount;
        this.takerPaybackAmount = takerPaybackAmount;
        this.offererPayoutAddress = offererPayoutAddress;
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    public String getDepositTxAsHex() {
        return depositTxAsHex;
    }

    public String getOffererPayoutAddress() {
        return offererPayoutAddress;
    }

    public String getOffererSignatureS() {
        return offererSignatureS;
    }

    public Coin getOffererPaybackAmount() {
        return offererPaybackAmount;
    }

    public Coin getTakerPaybackAmount() {
        return takerPaybackAmount;
    }

    public String getOffererSignatureR() {
        return offererSignatureR;
    }
}
