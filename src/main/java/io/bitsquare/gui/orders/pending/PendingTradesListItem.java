package io.bitsquare.gui.orders.pending;

import io.bitsquare.gui.trade.orderbook.OrderBookListItem;
import io.bitsquare.trade.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesListItem extends OrderBookListItem
{
    private static final Logger log = LoggerFactory.getLogger(PendingTradesListItem.class);


    private final Trade trade;

    public PendingTradesListItem(Trade trade)
    {
        super(trade.getOffer());
        this.trade = trade;
    }


    public Trade getTrade()
    {
        return trade;
    }

}
