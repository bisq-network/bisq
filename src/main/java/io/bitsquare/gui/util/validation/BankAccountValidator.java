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

import io.bitsquare.locale.BSResources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NumberValidator for validating basic number values.
 * Localisation not supported at the moment
 * The decimal mark can be either "." or ",". Thousand separators are not supported yet,
 * but might be added alter with Local support.
 * <p>
 * That class implements just what we need for the moment. It is not intended as a general purpose library class.
 */

// TODO Add validation for primary and secondary IDs according to the selected type
public class BankAccountValidator extends InputValidator {
    private static final Logger log = LoggerFactory.getLogger(BankAccountValidator.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid)
            result = validateMinLength(input);
        return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ValidationResult validateMinLength(String input) {
        if (input.length() > 3)
            return new ValidationResult(true);
        else
            return new ValidationResult(false, BSResources.get("validation.inputTooShort"));
    }

}
