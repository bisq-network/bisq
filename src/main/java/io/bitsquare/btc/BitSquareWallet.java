package io.bitsquare.btc;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.crypto.KeyCrypter;
import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BitSquareWallet extends Wallet implements Serializable
{
    private static final Logger log = LoggerFactory.getLogger(BitSquareWallet.class);
    private static final long serialVersionUID = -6231929674475881549L;

    public BitSquareWallet(NetworkParameters params)
    {
        super(params);
    }

    private BitSquareWallet(NetworkParameters params, KeyCrypter keyCrypter)
    {
        super(params, keyCrypter);
    }


}
