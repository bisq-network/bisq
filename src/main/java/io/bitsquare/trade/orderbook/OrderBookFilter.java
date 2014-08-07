package io.bitsquare.trade.orderbook;

import com.google.bitcoin.core.Coin;
import io.bitsquare.trade.Direction;
import javafx.beans.property.SimpleBooleanProperty;

public class OrderBookFilter
{
    private final SimpleBooleanProperty directionChangedProperty = new SimpleBooleanProperty();

    private double price;
    private Coin amount;
    private Direction direction;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getAmount()
    {
        return amount;
    }

    public void setAmount(Coin amount)
    {
        this.amount = amount;
    }

    public Direction getDirection()
    {
        return direction;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setDirection(Direction direction)
    {
        this.direction = direction;
        directionChangedProperty.set(!directionChangedProperty.get());
    }

    public double getPrice()
    {
        return price;
    }

    public void setPrice(double price)
    {
        this.price = price;
    }


    public SimpleBooleanProperty getDirectionChangedProperty()
    {
        return directionChangedProperty;
    }


}
