package io.bitsquare.gui.orders.pending;

import io.bitsquare.gui.market.orderbook.OrderBookListItem;
import io.bitsquare.trade.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradesTableItem extends OrderBookListItem
{
    private static final Logger log = LoggerFactory.getLogger(TradesTableItem.class);

    private Trade trade;

    public TradesTableItem(Trade trade)
    {
        super(trade.getOffer());

        this.trade = trade;
    }

    public Trade getTrade()
    {
        return trade;
    }

}
