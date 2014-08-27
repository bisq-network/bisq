/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BaseValidator for validating basic number values.
 * Localisation not supported at the moment
 * The decimal mark can be either "." or ",". Thousand separators are not supported yet,
 * but might be added alter with Local support.
 * <p>
 * That class implements just what we need for the moment. It is not intended as a general purpose library class.
 */
public abstract class InputValidator {
    private static final Logger log = LoggerFactory.getLogger(InputValidator.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract public ValidationResult validate(String input);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ValidationResult validateIfNotEmpty(String input) {
        if (input == null || input.length() == 0)
            return new ValidationResult(false, "Empty input is not allowed.", ErrorType.EMPTY_INPUT);
        else
            return new ValidationResult(true);
    }

    protected String cleanInput(String input) {
        return input.replace(",", ".").trim();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ErrorType
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum ErrorType {
        EMPTY_INPUT,
        NOT_A_NUMBER,
        ZERO_NUMBER,
        NEGATIVE_NUMBER,
        FRACTIONAL_SATOSHI,
        EXCEEDS_MAX_FIAT_VALUE, UNDERCUT_MIN_FIAT_VALUE, AMOUNT_LESS_THAN_MIN_AMOUNT,
        MIN_AMOUNT_LARGER_THAN_MIN_AMOUNT, EXCEEDS_MAX_BTC_VALUE
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ValidationResult
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        public final ErrorType errorType;

        public ValidationResult(boolean isValid, String errorMessage, ErrorType errorType) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
        }

        ValidationResult(boolean isValid) {
            this(isValid, null, null);
        }

        public ValidationResult and(ValidationResult next) {
            if (this.isValid)
                return next;
            else
                return this;
        }

        @Override
        public String toString() {
            return "ValidationResult{" +
                    "isValid=" + isValid +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", errorType=" + errorType +
                    '}';
        }
    }
}
