package io.bitsquare.btc;

import com.google.bitcoin.core.Transaction;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BtcValidatorTest
{
    @Test
    public void testIsMinSpendableAmount()
    {
        BigInteger amount = null;
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = BigInteger.ZERO;
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE;
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = Transaction.MIN_NONDUST_OUTPUT;
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT);
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT).add(BigInteger.ONE);
        assertTrue("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));
    }
}
