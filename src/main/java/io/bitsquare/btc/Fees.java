package io.bitsquare.btc;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;

import java.math.BigInteger;

public class Fees
{
    // min dust value lead to exception at for non standard to address pay scripts, so we use a value >= 7860 instead
    public static BigInteger TX_FEE = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
    public static BigInteger ACCOUNT_REGISTRATION_FEE = Utils.toNanoCoins("0.0002");
    public static BigInteger OFFER_CREATION_FEE = Utils.toNanoCoins("0.0002");
    public static BigInteger OFFER_TAKER_FEE = OFFER_CREATION_FEE;
}
