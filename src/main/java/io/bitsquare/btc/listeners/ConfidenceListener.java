package io.bitsquare.btc.listeners;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.TransactionConfidence;

public class ConfidenceListener
{
    private Address address;

    public ConfidenceListener(Address address)
    {
        this.address = address;
    }

    public Address getAddress()
    {
        return address;
    }

    public void onTransactionConfidenceChanged(TransactionConfidence confidence)
    {

    }
}