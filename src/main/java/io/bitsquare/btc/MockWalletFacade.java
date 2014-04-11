package io.bitsquare.btc;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Gateway to wallet
 */
public class MockWalletFacade implements IWalletFacade
{
    private static final Logger log = LoggerFactory.getLogger(MockWalletFacade.class);

    private BigInteger balance;

    public MockWalletFacade()
    {
        balance = new BigInteger("100000000");
    }

    @Override
    public BigInteger getBalance()
    {
        return balance;
    }

    @Override
    public boolean pay(BigInteger satoshisToPay, String destinationAddress)
    {
        if (getBalance().subtract(satoshisToPay).longValue() > 0)
        {
            log.info("Pay " + satoshisToPay.toString() + " Satoshis to " + destinationAddress);
            return true;
        }
        else
        {
            log.warn("Not enough funds in wallet for paying " + satoshisToPay.toString() + " Satoshis.");
            return false;
        }

    }

    @Override
    public KeyPair createNewAddress()
    {
        //MOCK
        return new KeyPair(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
}
