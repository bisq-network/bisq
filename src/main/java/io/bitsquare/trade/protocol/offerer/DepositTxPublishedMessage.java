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

public class DepositTxPublishedMessage implements Serializable, TradeMessage
{

    private static final long serialVersionUID = -1532231540167406581L;
    private final String tradeId;

    private String depositTxAsHex;

    public DepositTxPublishedMessage(String tradeId, String depositTxAsHex)
    {
        this.tradeId = tradeId;
        this.depositTxAsHex = depositTxAsHex;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public String getDepositTxAsHex()
    {
        return depositTxAsHex;
    }
}
