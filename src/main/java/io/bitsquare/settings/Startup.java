package io.bitsquare.settings;

import com.google.inject.Inject;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;

public class Startup
{
    private Storage storage;
    private User user;
    private OrderBookFilter orderBookFilter;

    @Inject
    public Startup(Storage storage, OrderBookFilter orderBookFilter)
    {
        this.storage = storage;
        this.orderBookFilter = orderBookFilter;
    }

    public void applyPersistedData()
    {


        // todo use persistence
        orderBookFilter.setAmount(0.0);
        orderBookFilter.setPrice(0.0);
        orderBookFilter.setDirection(Direction.BUY);
        orderBookFilter.setCurrency(Settings.getCurrency());
    }
}
