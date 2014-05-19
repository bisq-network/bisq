package io.bitsquare.btc;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.crypto.KeyCrypter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitSquareWallet extends Wallet
{
    private static final Logger log = LoggerFactory.getLogger(BitSquareWallet.class);

    public BitSquareWallet(NetworkParameters params)
    {
        super(params);
    }

    public BitSquareWallet(NetworkParameters params, KeyCrypter keyCrypter)
    {
        super(params, keyCrypter);
    }

}
