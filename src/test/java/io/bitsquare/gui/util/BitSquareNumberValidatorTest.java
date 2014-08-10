package io.bitsquare.gui.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitSquareNumberValidatorTest
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


}
