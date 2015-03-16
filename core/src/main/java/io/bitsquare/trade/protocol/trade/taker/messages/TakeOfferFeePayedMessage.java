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

package io.bitsquare.trade.protocol.trade.taker.messages;

import io.bitsquare.trade.protocol.trade.TradeMessage;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

public class TakeOfferFeePayedMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = -5057935061275354312L;

    private final String tradeId;
    private final Coin tradeAmount;
    private final String takeOfferFeeTxID;
    private final byte[] takerPubKey;

    public TakeOfferFeePayedMessage(String tradeId, String takeOfferFeeTxID, Coin tradeAmount, byte[] takerPubKey) {
        this.tradeId = tradeId;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.tradeAmount = tradeAmount;
        this.takerPubKey = takerPubKey;
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxID;
    }

    public byte[] getTakerPubKey() {
        return takerPubKey;
    }

}
