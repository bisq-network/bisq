package io.bitsquare.trade.orderbook;

import io.bitsquare.trade.Direction;
import javafx.beans.property.SimpleBooleanProperty;

public class OrderBookFilter
{
    transient private final SimpleBooleanProperty changedProperty = new SimpleBooleanProperty();

    private double price;
    private double amount;
    private Direction direction;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setAmount(double amount)
    {
        this.amount = amount;
        triggerChange();
    }

    public void setPrice(double price)
    {
        this.price = price;
        triggerChange();
    }

    public void setDirection(Direction direction)
    {
        this.direction = direction;
        triggerChange();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public double getAmount()
    {
        return amount;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public double getPrice()
    {
        return price;
    }

    public SimpleBooleanProperty getChangedProperty()
    {
        return changedProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void triggerChange()
    {
        changedProperty.set(!changedProperty.get());
    }

}
