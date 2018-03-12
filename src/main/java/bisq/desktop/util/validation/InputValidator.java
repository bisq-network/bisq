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

import bisq.common.locale.Res;

public class InputValidator {

    public ValidationResult validate(String input) {
        return validateIfNotEmpty(input);
    }

    protected ValidationResult validateIfNotEmpty(String input) {
        if (input == null || input.length() == 0)
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
    }

    protected boolean isPositiveNumber(String input) {
        try {
            return input != null && Long.parseLong(input) >= 0;
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
