package io.bitsquare.btc;

import java.math.BigInteger;

public class Fees
{
    // min dust value lead to exception at for non standard to address pay scripts, so we use a value >= 7860 instead
    public static BigInteger ACCOUNT_REGISTRATION_FEE = BigInteger.valueOf(7860);// Utils.toNanoCoins("0.001");
    public static BigInteger OFFER_CREATION_FEE = BigInteger.valueOf(7860); //  //Transaction.MIN_NONDUST_OUTPUT; // Utils.toNanoCoins("0.001");
    public static BigInteger OFFER_TAKER_FEE = BigInteger.valueOf(7860);
}
