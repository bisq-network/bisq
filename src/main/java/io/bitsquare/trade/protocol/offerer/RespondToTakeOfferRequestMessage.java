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

import io.bitsquare.trade.protocol.TradeMessage;

import java.io.Serializable;

public class RespondToTakeOfferRequestMessage implements Serializable, TradeMessage {
    private static final long serialVersionUID = 6177387534087739018L;
    private final String tradeId;
    private boolean takeOfferRequestAccepted;

    public RespondToTakeOfferRequestMessage(String tradeId, boolean takeOfferRequestAccepted) {
        this.tradeId = tradeId;
        this.takeOfferRequestAccepted = takeOfferRequestAccepted;
    }

    @Override
    public String getTradeId() {
        return tradeId;
    }

    public boolean isTakeOfferRequestAccepted() {
        return takeOfferRequestAccepted;
    }
}
