package io.bitsquare.btc;

import com.google.bitcoin.core.Transaction;

import java.math.BigInteger;

public class Fees
{

    public static BigInteger ACCOUNT_REGISTRATION_FEE = Transaction.MIN_NONDUST_OUTPUT;// Utils.toNanoCoins("0.001");
    public static BigInteger OFFER_CREATION_FEE = Transaction.MIN_NONDUST_OUTPUT; // Utils.toNanoCoins("0.001");
    public static BigInteger OFFER_TAKER_FEE = OFFER_CREATION_FEE;
}
