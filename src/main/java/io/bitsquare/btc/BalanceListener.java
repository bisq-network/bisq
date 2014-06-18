package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class BalanceListener
{
    private static final Logger log = LoggerFactory.getLogger(BalanceListener.class);
    private Address address;

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