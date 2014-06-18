package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfidenceListener
{
    private static final Logger log = LoggerFactory.getLogger(ConfidenceListener.class);
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