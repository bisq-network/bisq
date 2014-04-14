package io.bitsquare.btc;

import com.google.bitcoin.core.PeerEventListener;

import java.math.BigInteger;

public interface IWalletFacade
{
    public static final String MAIN_NET = "MAIN_NET";
    public static final String TEST_NET = "TEST_NET";
    public static final String REG_TEST_NET = "REG_TEST_NET";

    void initWallet(PeerEventListener peerEventListener);

    void terminateWallet();

    BigInteger getBalance();

    boolean pay(BigInteger satoshisToPay, String destinationAddress);

    KeyPair createNewAddress();
}
