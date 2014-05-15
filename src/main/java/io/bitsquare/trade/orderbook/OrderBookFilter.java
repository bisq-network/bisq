package io.bitsquare.trade.orderbook;

import io.bitsquare.trade.Direction;
import javafx.beans.property.SimpleBooleanProperty;

public class OrderBookFilter
{
    transient private final SimpleBooleanProperty directionChangedProperty = new SimpleBooleanProperty();

    private double price;
    private double amount;
    private Direction direction;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setAmount(double amount)
    {
        this.amount = amount;
    }

    public void setPrice(double price)
    {
        this.price = price;
    }

    public void setDirection(Direction direction)
    {
        this.direction = direction;
        directionChangedProperty.set(!directionChangedProperty.get());
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

    public SimpleBooleanProperty getDirectionChangedProperty()
    {
        return directionChangedProperty;
    }


}
