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

package bisq.core.util.validation;

import bisq.core.locale.Res;

import java.math.BigInteger;

import java.util.Objects;
import java.util.function.Function;

public class InputValidator {

    public ValidationResult validate(String input) {
        return validateIfNotEmpty(input);
    }

    protected ValidationResult validateIfNotEmpty(String input) {
        //trim added to avoid empty input
        if (input == null || input.trim().length() == 0)
            return new ValidationResult(false, Res.get("validation.empty"));
        else
            return new ValidationResult(true);
    }

    public static class ValidationResult {
        public final boolean isValid;
        public final String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        public ValidationResult(boolean isValid) {
            this(isValid, null);
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
                    '}';
        }

        public boolean errorMessageEquals(ValidationResult other) {
            if (this == other) return true;
            if (other == null) return false;
            return Objects.equals(errorMessage, other.errorMessage);
        }

        public interface Validator extends Function<String, ValidationResult> {

        }

        /*
            This function validates the input with array of validator functions.
            If any function validation result is false, it short circuits
            as in && (and) operation.
        */
        public ValidationResult andValidation(String input, Validator... validators) {
            ValidationResult result = null;
            for (Validator validator : validators) {
                result = validator.apply(input);
                if (!result.isValid)
                    return result;
            }
            return result;
        }
    }

    protected boolean isPositiveNumber(String input) {
        try {
            return input != null && new BigInteger(input).compareTo(BigInteger.ZERO) >= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    protected boolean isNumberWithFixedLength(String input, int length) {
        return isPositiveNumber(input) && input.length() == length;
    }

    protected boolean isNumberInRange(String input, int minLength, int maxLength) {
        return isPositiveNumber(input) && input.length() >= minLength && input.length() <= maxLength;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean isStringWithFixedLength(String input, int length) {
        return input != null && input.length() == length;
    }

    protected boolean isStringInRange(String input, int minLength, int maxLength) {
        return input != null && input.length() >= minLength && input.length() <= maxLength;
    }
}
