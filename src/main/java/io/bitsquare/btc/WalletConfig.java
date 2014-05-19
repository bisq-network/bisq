package io.bitsquare.btc;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.kits.WalletAppKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class WalletConfig extends WalletAppKit
{
    private static final Logger log = LoggerFactory.getLogger(WalletConfig.class);


    public WalletConfig(NetworkParameters params, File directory, String filePrefix)
    {
        super(params, directory, filePrefix);
    }


}
