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

package io.bitsquare.gui.orders.pending;

import io.bitsquare.gui.trade.orderbook.OrderBookListItem;
import io.bitsquare.trade.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesListItem extends OrderBookListItem {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesListItem.class);


    private final Trade trade;

    public PendingTradesListItem(Trade trade) {
        super(trade.getOffer());
        this.trade = trade;
    }


    public Trade getTrade() {
        return trade;
    }

}
