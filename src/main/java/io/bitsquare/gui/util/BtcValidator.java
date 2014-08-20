package io.bitsquare.gui.util;

import com.google.bitcoin.core.NetworkParameters;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BtcValidator for validating BTC values.
 * <p>
 * That class implements just what we need for the moment. It is not intended as a general purpose library class.
 */
public class BtcValidator extends NumberValidator
{
    private static final Logger log = LoggerFactory.getLogger(BtcValidator.class);
    private ValidationResult externalValidationResult;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input)
    {
        if (externalValidationResult != null)
            return externalValidationResult;

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
                    .and(validateIfNotFractionalBtcValue(input))
                    .and(validateIfNotExceedsMaxBtcValue(input));
        }

        return result;
    }

    /**
     * Used to integrate external validation (e.g. for MinAmount/Amount)
     * TODO To be improved but does the job for now...
     *
     * @param externalValidationResult
     */
    public void overrideResult(ValidationResult externalValidationResult)
    {
        this.externalValidationResult = externalValidationResult;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ValidationResult validateIfNotFractionalBtcValue(String input)
    {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(8);
        if (satoshis.scale() > 0)
            return new ValidationResult(false, "Input results in a Bitcoin value with a fraction of the smallest unit (Satoshi).", ErrorType.FRACTIONAL_SATOSHI);
        else
            return new ValidationResult(true);
    }

    protected ValidationResult validateIfNotExceedsMaxBtcValue(String input)
    {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(8);
        if (satoshis.longValue() > NetworkParameters.MAX_MONEY.longValue())
            return new ValidationResult(false, "Input larger as maximum possible Bitcoin value is not allowed.", ErrorType.EXCEEDS_MAX_BTC_VALUE);
        else
            return new ValidationResult(true);
    }
}
