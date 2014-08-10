package io.bitsquare.gui.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FiatValidatorTest
{
    @Test
    public void testValidate()
    {
        FiatValidator validator = new FiatValidator();
        NumberValidator.ValidationResult validationResult;


        // invalid cases
        validationResult = validator.validate(null);
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("0");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("-1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("a");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("2a");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("a2");
        assertFalse(validationResult.isValid);

        // at the moment we dont support thousand separators, can be added later
        validationResult = validator.validate("1,100.1");
        assertFalse(validationResult.isValid);

        // at the moment we dont support thousand separators, can be added later
        validationResult = validator.validate("1.100,1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("1.100.1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate("1,100,1");
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MIN_FIAT_VALUE - 0.0000001));
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MAX_FIAT_VALUE + 0.0000001));
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(Double.MIN_VALUE));
        assertFalse(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(Double.MAX_VALUE));
        assertFalse(validationResult.isValid);


        // valid cases
        validationResult = validator.validate("1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate("0,1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate("0.1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(",1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(".1");
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MIN_FIAT_VALUE));
        assertTrue(validationResult.isValid);

        validationResult = validator.validate(String.valueOf(FiatValidator.MAX_FIAT_VALUE));
        assertTrue(validationResult.isValid);
    }
}
