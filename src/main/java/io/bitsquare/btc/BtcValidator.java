package io.bitsquare.btc;

import com.google.bitcoin.core.Transaction;

import java.math.BigInteger;

public class BtcValidator
{
    public static boolean isMinSpendableAmount(BigInteger amount)
    {
        return amount != null && amount.compareTo(FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT)) > 0;
    }


}
