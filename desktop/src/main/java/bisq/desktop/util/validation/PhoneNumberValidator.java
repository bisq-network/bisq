/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;

import lombok.extern.slf4j.Slf4j;

import static com.google.i18n.phonenumbers.NumberParseException.ErrorType.*;



import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * PhoneNumberValidator validates and converts phone numbers into E164 international form.
 *
 * @see <a href="https://en.wikipedia.org/wiki/E.164">https://en.wikipedia.org/wiki/E.164</a>
 */
@Slf4j
public final class PhoneNumberValidator extends InputValidator {
    /**
     * Google libphonenumber utility to validate and format string inputs.
     */
    private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    /**
     * The international E.164 format is designed to include all necessary
     * information to route a call or SMS message to an individual subscriber
     * on a nation's public telephone network.
     */
    private static final PhoneNumberFormat intlPhoneNumberFormat = PhoneNumberFormat.E164;
    private final String isoCountryCode;
    private ValidationResult validationResult;

    /**
     * Phone number in E.164 format.
     */
    private String normalizedPhoneNumber;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Hide no-arg constructor
    private PhoneNumberValidator() {
        this.isoCountryCode = null;
    }

    public PhoneNumberValidator(String isoCountryCode) {
        this.isoCountryCode = isoCountryCode;
    }

    /**
     * Validates a phone number for a country as follows.
     * <p>
     * The countryCode is validated.  If null or empty, no further validation
     * is made and a false ValidationResult is returned with an appropriate error message.
     * <p>
     * A false ValidationResult is returned if the input phone number is null or empty.
     * <p>
     * The input phone number is parsed, and its validity for the given countryCode
     * is checked.  A false ValidationResult is returned if the phone number is not valid
     * for the country,
     * <p>
     * If the phone number passes validation tests, it is transformed into E.164
     * format and cached in the normalizedPhoneNumber field.
     * @see {@link #getNormalizedPhoneNumber()}
     *
     * @param input  Phone number to be validated and normalized
     * @return Value for property 'validationResult'.
     */
    public ValidationResult validate(String input) {
        normalizedPhoneNumber = null;
        if (isoCountryCode == null || isoCountryCode.isEmpty()) {
            validationResult =
                    new ValidationResult(false,
                            Res.get("validation.phone.needCountryToValidatePhone",
                                    (input == null ? "" : input)));
        } else {
            validationResult = super.validate(input);
            if (validationResult.isValid) {
                try {
                    final PhoneNumber phoneNumber = phoneNumberUtil.parse(input, isoCountryCode);
                    if (!phoneNumberUtil.isValidNumber(phoneNumber)) {
                        validationResult =
                                new ValidationResult(false, Res.get("validation.phone.invalidPhoneInCountry",
                                        input, isoCountryCode));
                        normalizedPhoneNumber = null;
                    } else {
                        normalizedPhoneNumber = phoneNumberUtil.format(phoneNumber, intlPhoneNumberFormat);
                    }
                    return validationResult;
                } catch (NumberParseException e) {
                    final ErrorType parseErrorType = e.getErrorType();
                    if (parseErrorType == INVALID_COUNTRY_CODE) {
                        // Error type: INVALID_COUNTRY_CODE. Missing or invalid default region.
                        validationResult = new ValidationResult(false, Res.get("validation.phone.missingCountryDialingCode"));
                    } else if (parseErrorType == NOT_A_NUMBER) {
                        // Error type: NOT_A_NUMBER. The string supplied did not seem to be a phone number.
                        validationResult = new ValidationResult(false, Res.get("validation.phone.notANumber", input));
                    } else if (parseErrorType == TOO_SHORT_AFTER_IDD) {
                        // Have not been able to generate this error yet
                        log.warn(e.getMessage(), e);
                        validationResult = new ValidationResult(false, Res.get("validation.phone.notANumber", input));
                    } else if (parseErrorType == TOO_SHORT_NSN) {
                        // Have not been able to generate this error yet
                        log.warn(e.getMessage(), e);
                        validationResult = new ValidationResult(false, Res.get("validation.phone.notANumber", input));
                    } else if (parseErrorType == TOO_LONG) {
                        // Error type: TOO_LONG. The string supplied is too long to be a phone number.
                        validationResult = new ValidationResult(false, Res.get("validation.phone.numberTooLong", input));
                    } else {
                        log.warn(e.getMessage(), e);
                        validationResult = new ValidationResult(false, e.getLocalizedMessage());
                    }
                }
            }
        }
        return validationResult;
    }

    /**
     * Getter for property 'normalizedPhoneNumber'.
     *
     * @return Value for property 'normalizedPhoneNumber'.
     */
    public String getNormalizedPhoneNumber() {
        return normalizedPhoneNumber;
    }

    /**
     * Getter for property 'validationResult'.
     *
     * @return Value for property 'validationResult'.
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
