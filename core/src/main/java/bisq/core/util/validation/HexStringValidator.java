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

import bisq.common.util.Utilities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

@EqualsAndHashCode(callSuper = true)
@Data
public class HexStringValidator extends InputValidator {
    @Setter
    private int minLength = Integer.MIN_VALUE;
    @Setter
    private int maxLength = Integer.MAX_VALUE;

    public HexStringValidator() {
    }

    public ValidationResult validate(String input) {
        ValidationResult validationResult = super.validate(input);
        if (!validationResult.isValid)
            return validationResult;

        if (input.length() > maxLength || input.length() < minLength)
            new ValidationResult(false, Res.get("validation.length", minLength, maxLength));

        try {
            Utilities.decodeFromHex(input);
            return validationResult;
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.noHexString", input));
        }

    }
}
