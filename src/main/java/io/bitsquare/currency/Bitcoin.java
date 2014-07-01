package io.bitsquare.currency;

import com.google.bitcoin.core.Transaction;
import io.bitsquare.btc.BtcFormatter;
import java.math.BigInteger;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bitcoin extends BigInteger
{
    private static final Logger log = LoggerFactory.getLogger(Bitcoin.class);
    private static final long serialVersionUID = 6436341706716520132L;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Bitcoin(BigInteger val)
    {
        super(val.toByteArray());
    }

    public Bitcoin(byte[] val)
    {
        super(val);
    }

    public Bitcoin(int signum, byte[] magnitude)
    {
        super(signum, magnitude);
    }

    public Bitcoin(String val, int radix)
    {
        super(val, radix);
    }

    public Bitcoin(String val)
    {
        super(val);
    }

    public Bitcoin(int numBits, Random rnd)
    {
        super(numBits, rnd);
    }

    public Bitcoin(int bitLength, int certainty, Random rnd)
    {
        super(bitLength, certainty, rnd);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isZero()
    {

        return this.compareTo(BigInteger.ZERO) == 0;
    }

    public boolean isMinValue()
    {

        return this.compareTo(Transaction.MIN_NONDUST_OUTPUT) >= 0;
    }

    public Bitcoin addBitcoin(Bitcoin other)
    {
        return new Bitcoin(this.add(other));
    }

    public Bitcoin subtractBitcoin(Bitcoin other)
    {
        return new Bitcoin(this.subtract(other));
    }

    public Bitcoin multiplyBitcoin(Bitcoin other)
    {
        return new Bitcoin(this.multiply(other));
    }

    public boolean isLarger(Bitcoin other)
    {

        return this.compareTo(other) > 0;
    }

    public boolean isLess(Bitcoin other)
    {

        return this.compareTo(other) < 0;
    }

    public boolean isEqual(Bitcoin other)
    {

        return this.compareTo(other) == 0;
    }

    public String getFormattedValue()
    {
        return BtcFormatter.satoshiToString(this);
    }


}
