package io.bitsquare.trade.orderbook;

import io.bitsquare.gui.trade.orderbook.OrderBookListItem;
import javafx.collections.ObservableList;

public interface IOrderBook
{
    ObservableList<OrderBookListItem> getFilteredList(OrderBookFilter orderBookFilter);
}
