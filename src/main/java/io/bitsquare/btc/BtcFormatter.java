package io.bitsquare.btc;

import java.math.BigInteger;

public class BtcFormatter
{
    public static BigInteger BTC = new BigInteger("100000000");
    public static BigInteger mBTC = new BigInteger("100000");

    //TODO
    public static double satoshiToBTC(BigInteger satoshis)
    {
        return satoshis.doubleValue() / BTC.doubleValue();
    }
}
