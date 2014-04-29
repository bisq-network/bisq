package io.bitsquare.trade.orderbook;

import io.bitsquare.trade.Direction;
import io.bitsquare.trade.OfferConstraints;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.Currency;
import java.util.Locale;

public class OrderBookFilter
{
    private double price;
    private double amount;
    private Direction direction;
    private Currency currency;
    private Locale countryLocale;
    private Locale languageLocale;
    private OfferConstraints offerConstraints;


    public SimpleBooleanProperty changedPropertyProperty()
    {
        return changedProperty;
    }

    private final SimpleBooleanProperty changedProperty = new SimpleBooleanProperty();

    // setters
    public void setCurrency(Currency currency)
    {
        this.currency = currency;
        triggerChange();
    }

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

    public void setOfferConstraints(OfferConstraints offerConstraints)
    {
        this.offerConstraints = offerConstraints;
        triggerChange();
    }

    public void setCountryLocale(Locale countryLocale)
    {
        this.countryLocale = countryLocale;
        triggerChange();
    }

    public void setLanguageLocale(Locale languageLocale)
    {
        this.languageLocale = languageLocale;
        triggerChange();
    }

    // getters
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

    public Currency getCurrency()
    {
        return currency;
    }

    public OfferConstraints getOfferConstraints()
    {
        return offerConstraints;
    }

    public Locale getCountryLocale()
    {
        return countryLocale;
    }

    public Locale getLanguageLocale()
    {
        return languageLocale;
    }

    public SimpleBooleanProperty getChangedProperty()
    {
        return changedProperty;
    }

    private void triggerChange()
    {
        changedProperty.set(!changedProperty.get());
    }
}
