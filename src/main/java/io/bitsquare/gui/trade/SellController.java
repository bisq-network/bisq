package io.bitsquare.gui.trade;

import io.bitsquare.trade.Direction;

public class SellController extends TradeController
{
    @Override
    protected void applyDirection()
    {
        orderBookController.applyDirection(Direction.SELL);
    }


}

