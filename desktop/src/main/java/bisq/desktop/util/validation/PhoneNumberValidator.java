package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;

import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Performs lenient validation of international phone numbers, and transforms given
 * input numbers into E.164 international form.  The E.164 normalized phone number
 * can be accessed via {@link #getNormalizedPhoneNumber} after successful
 * validation -- {@link #getNormalizedPhoneNumber} will return null if validation
 * fails.
 * <p>
 * Area codes and mobile provider codes are not validated, but all numbers following
 * calling codes are included in the normalized number.
 * <p>
 * @see  bisq.desktop.util.validation.CountryCallingCodes
 */
public class PhoneNumberValidator extends InputValidator {
    /**
     * ISO 3166-1 alpha-2 country code
     */
    private String isoCountryCode;
    /**
     * The international calling code mapped to the 'isoCountryCode' constructor argument.
     */
    @Nullable
    @Getter
    private String callingCode;
    /**
     * The normalized (digits only) representation of an international calling code.
     */
    private String normalizedCallingCode;
    /**
     * Phone number in E.164 format.
     */
    @Nullable
    @Getter
    private String normalizedPhoneNumber;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Public no-arg constructor required by Guice injector,
    // but isoCountryCode must be set before validation.
    public PhoneNumberValidator() {
    }
    
    public PhoneNumberValidator(String isoCountryCode) {
        this.isoCountryCode = isoCountryCode;
        this.callingCode = CountryCallingCodes.getCallingCode(isoCountryCode);
        this.normalizedCallingCode = CountryCallingCodes.getNormalizedCallingCode(isoCountryCode);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input) {
        normalizedPhoneNumber = null;
        ValidationResult result = super.validate(isoCountryCode);
        if (!result.isValid) {
            return new ValidationResult(false, Res.get("validation.phone.missingCountryCode"));
        }
        result = super.validate(input);
        if (!result.isValid) {
            return result;
        }
        String trimmedInput = input.trim();
        boolean isCountryDialingCodeExplicit = trimmedInput.startsWith("+");
        // Remove non-alphanumeric chars.  Letters may be left in the pureNumber string for
        // the isPositiveNumber(pureNumber) test.  The US once had listed numbers with letters
        // -- I had one -- and I'm sure they are still used in some countries.  In such cases,
        // users will be prompted to convert letters to numbers for normalization.
        String pureNumber = input.replaceAll("[^A-Za-z0-9]", "");
        boolean hasValidCallingCodePrefix = pureNumber.startsWith(normalizedCallingCode);
        boolean isCountryCallingCodeImplicit = !isCountryDialingCodeExplicit && hasValidCallingCodePrefix;

        result = validateIsNumeric(input, pureNumber)
                .and(validateHasSufficientDigits(input, pureNumber))
                .and(validateIsNotTooLong(input, pureNumber))
                .and(validateIncludedCountryDialingCode(input, isCountryDialingCodeExplicit, hasValidCallingCodePrefix));

        if (result.isValid) {
            // TODO corner cases: isCountryCallingCodeImplicit == true
            //  && the country calling code is matched by following digits.
            //  && Is this a problem?
            if (isCountryDialingCodeExplicit || isCountryCallingCodeImplicit) {
                normalizedPhoneNumber = "+" + pureNumber;
            } else {
                normalizedPhoneNumber = "+" + getCallingCode() + pureNumber;
            }
        }
        return result;
    }

    /**
     * Setter for property 'isoCountryCode'.
     *
     * @param isoCountryCode Value to set for property 'isoCountryCode'.
     */
    public void setIsoCountryCode(String isoCountryCode) {
        this.isoCountryCode = isoCountryCode;
        this.callingCode = CountryCallingCodes.getCallingCode(isoCountryCode);
        this.normalizedCallingCode = CountryCallingCodes.getNormalizedCallingCode(isoCountryCode);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ValidationResult validateIsNumeric(String rawInput, String pureNumber) {
        try {
            if (isPositiveNumber(pureNumber)) {
                return new ValidationResult(true);
            } else {
                return new ValidationResult(false, Res.get("validation.phone.invalidCharacters", rawInput));
            }
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateHasSufficientDigits(String rawInput, String pureNumber) {
        try {
            return ((pureNumber.length() - callingCode.length()) > 4)
                    ? new ValidationResult(true)
                    : new ValidationResult(false, Res.get("validation.phone.insufficientDigits", rawInput));
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateIsNotTooLong(String rawInput, String pureNumber) {
        try {
            return ((pureNumber.length() - callingCode.length()) > 12)
                    ? new ValidationResult(false, Res.get("validation.phone.tooManyDigits", rawInput))
                    : new ValidationResult(true);

        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateIncludedCountryDialingCode(String rawInput,
                                                                boolean isCountryDialingCodeExplicit,
                                                                boolean hasValidDialingCodePrefix) {
        try {
            if (isCountryDialingCodeExplicit && !hasValidDialingCodePrefix) {
                return new ValidationResult(false,
                        Res.get("validation.phone.invalidDialingCode", rawInput, isoCountryCode, callingCode));
            } else {
                return new ValidationResult(true);
            }
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
