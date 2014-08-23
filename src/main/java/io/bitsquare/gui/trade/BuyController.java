package io.bitsquare.gui.trade;

import io.bitsquare.trade.Direction;

public class BuyController extends TradeController
{
    @Override
    protected void applyDirection()
    {
        //tabPane.getSelectionModel().select(0);
        orderBookController.applyDirection(Direction.BUY);
    }
}

