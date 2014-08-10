package io.bitsquare.gui.util;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.NetworkParameters;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BtcValidatorTest
{
    @Test
    public void testValidate()
    {
        BtcValidator validator = new BtcValidator();
        NumberValidator.ValidationResult validationResult;

        // invalid cases
        validationResult = validator.validate("0.000000011");// minBtc is "0.00000001"
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("21000001"); //maxBtc is "21000000"
        assertFalse(validationResult.isValid);

        // valid cases
        String minBtc = Coin.SATOSHI.toPlainString(); // "0.00000001"
        validationResult = validator.validate(minBtc);
        assertTrue(validationResult.isValid);

        String maxBtc = Coin.valueOf(NetworkParameters.MAX_MONEY.longValue()).toPlainString(); //"21000000"
        validationResult = validator.validate(maxBtc);
        assertTrue(validationResult.isValid);
    }

}
