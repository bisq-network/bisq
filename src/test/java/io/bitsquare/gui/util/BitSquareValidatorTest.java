package io.bitsquare.gui.util;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.locale.Country;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.user.Arbitrator;
import java.math.BigInteger;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitSquareValidatorTest
{
    @Test
    public void testValidateStringAsDouble()
    {
        assertTrue(BitSquareValidator.validateStringAsDouble("0"));
        assertTrue(BitSquareValidator.validateStringAsDouble("1"));
        assertTrue(BitSquareValidator.validateStringAsDouble("0,1"));
        assertTrue(BitSquareValidator.validateStringAsDouble("0.01"));

        assertFalse(BitSquareValidator.validateStringAsDouble(""));
        assertFalse(BitSquareValidator.validateStringAsDouble("a"));
        assertFalse(BitSquareValidator.validateStringAsDouble("0.0.1"));
        assertFalse(BitSquareValidator.validateStringAsDouble("1,000.1"));
        assertFalse(BitSquareValidator.validateStringAsDouble("1.000,1"));
        assertFalse(BitSquareValidator.validateStringAsDouble(null));
    }

    @Test
    public void testValidateStringNotEmpty()
    {
        assertTrue(BitSquareValidator.validateStringNotEmpty("a"));
        assertTrue(BitSquareValidator.validateStringNotEmpty("123"));

        assertFalse(BitSquareValidator.validateStringNotEmpty(""));
        assertFalse(BitSquareValidator.validateStringNotEmpty(" "));
        assertFalse(BitSquareValidator.validateStringNotEmpty(null));
    }

    @Test
    public void testTradeAmountOutOfRange()
    {

        assertFalse(BitSquareValidator.tradeAmountOutOfRange(BigInteger.ZERO, new MockOffer(BigInteger.TEN, BigInteger.valueOf(0))));
        assertTrue(BitSquareValidator.tradeAmountOutOfRange(BigInteger.ZERO, new MockOffer(BigInteger.TEN, BigInteger.valueOf(1))));
        assertFalse(BitSquareValidator.tradeAmountOutOfRange(BigInteger.TEN, new MockOffer(BigInteger.TEN, BigInteger.valueOf(0))));
        assertFalse(BitSquareValidator.tradeAmountOutOfRange(BigInteger.TEN, new MockOffer(BigInteger.TEN, BigInteger.valueOf(2))));
        assertTrue(BitSquareValidator.tradeAmountOutOfRange(BigInteger.TEN, new MockOffer(BigInteger.ONE, BigInteger.valueOf(0))));
    }

    @Test
    public void testGreaterThanZero()
    {
        assertFalse(BitSquareValidator.greaterThanZero(BigInteger.ZERO));
        assertFalse(BitSquareValidator.greaterThanZero(BigInteger.valueOf(-1)));
        assertTrue(BitSquareValidator.greaterThanZero(BigInteger.ONE));
    }

    public static class MockOffer extends Offer
    {

        public MockOffer(BigInteger amount, BigInteger minAmount)
        {
            super(null, null, 0, amount, minAmount, null, null, null, null, null, 0, null, null);
        }

        public MockOffer(String messagePubKeyAsHex, Direction direction, double price, BigInteger amount, BigInteger minAmount, BankAccountType bankAccountType, Currency currency, Country bankAccountCountry, String bankAccountUID, Arbitrator arbitrator, int collateral, List<Country> acceptedCountries, List<Locale> acceptedLanguageLocales)
        {
            super(messagePubKeyAsHex, direction, price, amount, minAmount, bankAccountType, currency, bankAccountCountry, bankAccountUID, arbitrator, collateral, acceptedCountries, acceptedLanguageLocales);
        }
    }

}
