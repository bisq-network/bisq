package io.bitsquare.trade.orderbook;

import io.bitsquare.trade.Direction;
import io.bitsquare.trade.OfferConstraints;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Currency;

public class OrderBookFilter
{
    private double price;
    private double amount;
    private Direction direction;
    private Currency currency;
    private final StringProperty currencyProperty = new SimpleStringProperty();
    private OfferConstraints offerConstraints;

    public void setAmount(double amount)
    {
        this.amount = amount;
    }

    public double getAmount()
    {
        return amount;
    }

    public void setDirection(Direction direction)
    {
        this.direction = direction;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public double getPrice()
    {
        return price;
    }

    public void setPrice(double price)
    {
        this.price = price;
    }

    public Currency getCurrency()
    {
        return currency;
    }

    public StringProperty getCurrencyProperty()
    {
        return currencyProperty;
    }

    public void setCurrency(Currency currency)
    {
        this.currency = currency;
        currencyProperty.set(currency.getCurrencyCode());
    }

    public OfferConstraints getOfferConstraints()
    {
        return offerConstraints;
    }

    public void setOfferConstraints(OfferConstraints offerConstraints)
    {
        this.offerConstraints = offerConstraints;
    }
}
