package io.bitsquare.trade;

import io.bitsquare.user.User;

import java.util.Currency;
import java.util.UUID;

public class Offer
{
    private UUID uid;
    private double price;
    private double amount;
    private double minAmount;
    private Direction direction;
    private Currency currency;
    private User offerer;
    private OfferConstraints offerConstraints;

    public Offer(UUID uid,
                 Direction direction,
                 double price,
                 double amount,
                 double minAmount,
                 Currency currency,
                 User offerer,
                 OfferConstraints offerConstraints)
    {
        this.uid = uid;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.minAmount = minAmount;
        this.currency = currency;
        this.offerer = offerer;
        this.offerConstraints = offerConstraints;
    }

    public double getVolume()
    {
        return price * amount;
    }

    public double getMinVolume()
    {
        return price * minAmount;
    }

    public UUID getUid()
    {
        return uid;
    }

    public void setUid(UUID uid)
    {
        this.uid = uid;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public void setDirection(Direction direction)
    {
        this.direction = direction;
    }

    public double getPrice()
    {
        return price;
    }

    public void setPrice(double price)
    {
        this.price = price;
    }

    public double getAmount()
    {
        return amount;
    }

    public void setAmount(double amount)
    {
        this.amount = amount;
    }

    public double getMinAmount()
    {
        return minAmount;
    }

    public void setMinAmount(double minAmount)
    {
        this.minAmount = minAmount;
    }

    public Currency getCurrency()
    {
        return currency;
    }

    public void setCurrency(Currency currency)
    {
        this.currency = currency;
    }


    public OfferConstraints getOfferConstraints()
    {
        return offerConstraints;
    }

    public void setOfferConstraints(OfferConstraints offerConstraints)
    {
        this.offerConstraints = offerConstraints;
    }


    public User getOfferer()
    {
        return offerer;
    }

    public void setOfferer(User offerer)
    {
        this.offerer = offerer;
    }
}
