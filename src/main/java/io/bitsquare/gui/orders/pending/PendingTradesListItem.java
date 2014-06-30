package io.bitsquare.gui.orders.pending;

import io.bitsquare.gui.market.orderbook.OrderBookListItem;
import io.bitsquare.trade.Trade;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesListItem extends OrderBookListItem
{
    private static final Logger log = LoggerFactory.getLogger(PendingTradesListItem.class);

    @NotNull
    private final Trade trade;

    public PendingTradesListItem(@NotNull Trade trade)
    {
        super(trade.getOffer());
        this.trade = trade;
    }

    @NotNull
    public Trade getTrade()
    {
        return trade;
    }

}
