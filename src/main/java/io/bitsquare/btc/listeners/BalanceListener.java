package io.bitsquare.btc.listeners;

import com.google.bitcoin.core.Address;

import java.math.BigInteger;

public class BalanceListener
{
    private Address address;

    public BalanceListener()
    {
    }

    public BalanceListener(Address address)
    {
        this.address = address;
    }

    public Address getAddress()
    {
        return address;
    }

    public void onBalanceChanged(BigInteger balance)
    {
    }
}