package io.bitsquare.btc;

import java.math.BigInteger;

public interface IWalletFacade
{
    BigInteger getBalance();

    boolean pay(BigInteger satoshisToPay, String destinationAddress);

    KeyPair createNewAddress();
}
