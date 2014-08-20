package io.bitsquare.gui.market;

import io.bitsquare.trade.Direction;

public class BuyController extends SellController
{
    @Override
    protected void applyDirection()
    {
        //tabPane.getSelectionModel().select(0);
        orderBookController.applyDirection(Direction.BUY);
    }
}

