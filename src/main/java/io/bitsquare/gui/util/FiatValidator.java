package io.bitsquare.gui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FiatNumberValidator for validating fiat values.
 * <p>
 * That class implements just what we need for the moment. It is not intended as a general purpose library class.
 */
public class FiatValidator extends NumberValidator
{
    private static final Logger log = LoggerFactory.getLogger(FiatValidator.class);

    //TODO Find appropriate values - depends on currencies
    public static final double MIN_FIAT_VALUE = 0.01; // usually a cent is the smallest currency unit
    public static final double MAX_FIAT_VALUE = 1000000;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input)
    {
        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid)
        {
            input = cleanInput(input);
            result = validateIfNumber(input);
        }

        if (result.isValid)
        {
            result = validateIfNotZero(input)
                    .and(validateIfNotNegative(input))
                    .and(validateIfNotExceedsMinFiatValue(input))
                    .and(validateIfNotExceedsMaxFiatValue(input));
        }

        return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ValidationResult validateIfNotExceedsMinFiatValue(String input)
    {
        double d = Double.parseDouble(input);
        if (d < MIN_FIAT_VALUE)
            return new ValidationResult(false, "Input smaller as minimum possible Fiat value is not allowed..", ErrorType.UNDERCUT_MIN_FIAT_VALUE);
        else
            return new ValidationResult(true);
    }

    protected ValidationResult validateIfNotExceedsMaxFiatValue(String input)
    {
        double d = Double.parseDouble(input);
        if (d > MAX_FIAT_VALUE)
            return new ValidationResult(false, "Input larger as maximum possible Fiat value is not allowed.", ErrorType.EXCEEDS_MAX_FIAT_VALUE);
        else
            return new ValidationResult(true);
    }
}
