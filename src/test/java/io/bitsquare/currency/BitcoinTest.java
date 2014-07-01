package io.bitsquare.currency;

import com.google.bitcoin.core.Transaction;
import java.math.BigInteger;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitcoinTest
{
    @Test
    public void testBitcoin()
    {
        Bitcoin bitcoin;
        Bitcoin compareValue;

        bitcoin = new Bitcoin("0");
        assertTrue(bitcoin.isZero());

        bitcoin = new Bitcoin("0");
        compareValue = new Bitcoin("1");
        assertTrue(bitcoin.isLess(compareValue));
        assertFalse(compareValue.isLess(bitcoin));
        assertFalse(compareValue.isEqual(bitcoin));

        bitcoin = new Bitcoin("1");
        compareValue = new Bitcoin("0");
        assertFalse(bitcoin.isLess(compareValue));
        assertTrue(compareValue.isLess(bitcoin));
        assertFalse(compareValue.isEqual(bitcoin));

        bitcoin = new Bitcoin("1");
        compareValue = new Bitcoin("1");
        assertFalse(bitcoin.isLess(compareValue));
        assertFalse(compareValue.isLess(bitcoin));
        assertTrue(compareValue.isEqual(bitcoin));

        bitcoin = new Bitcoin(Transaction.MIN_NONDUST_OUTPUT);
        assertTrue(bitcoin.isMinValue());

        bitcoin = new Bitcoin(Transaction.MIN_NONDUST_OUTPUT.subtract(BigInteger.ONE));
        assertFalse(bitcoin.isMinValue());

        bitcoin = new Bitcoin(Transaction.MIN_NONDUST_OUTPUT.add(BigInteger.ONE));
        assertTrue(bitcoin.isMinValue());

    }

}
