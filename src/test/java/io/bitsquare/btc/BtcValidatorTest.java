package io.bitsquare.btc;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.Transaction;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BtcValidatorTest
{
    @Test
    public void testIsMinSpendableAmount()
    {
        Coin amount = null;
        //noinspection ConstantConditions
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = Coin.ZERO;
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE;
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = Transaction.MIN_NONDUST_OUTPUT;
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT);
        assertFalse("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));

        amount = FeePolicy.TX_FEE.add(Transaction.MIN_NONDUST_OUTPUT).add(Coin.valueOf(1));
        assertTrue("tx unfunded, pending", BtcValidator.isMinSpendableAmount(amount));
    }
}
