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

import com.google.bitcoin.core.NetworkParameters;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BtcValidator for validating BTC values.
 * <p>
 * That class implements just what we need for the moment. It is not intended as a general purpose library class.
 */
public final class BtcValidator extends NumberValidator {
    private static final Logger log = LoggerFactory.getLogger(BtcValidator.class);
    private ValidationResult externalValidationResult;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ValidationResult validate(String input) {
        if (externalValidationResult != null)
            return externalValidationResult;

        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid) {
            input = cleanInput(input);
            result = validateIfNumber(input);
        }

        if (result.isValid) {
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
    public void overrideResult(ValidationResult externalValidationResult) {
        this.externalValidationResult = externalValidationResult;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ValidationResult validateIfNotFractionalBtcValue(String input) {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(8);
        if (satoshis.scale() > 0)
            return new ValidationResult(false, BSResources.get("validation.btc.toSmall"));
        else
            return new ValidationResult(true);
    }

    private ValidationResult validateIfNotExceedsMaxBtcValue(String input) {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(8);
        if (satoshis.longValue() > NetworkParameters.MAX_MONEY.longValue())
            return new ValidationResult(false, BSResources.get("validation.btc.toLarge"));
        else
            return new ValidationResult(true);
    }
}
