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

import java.net.URL;

import static com.google.common.base.Preconditions.checkArgument;

public class UrlInputValidator extends InputValidator {

    public UrlInputValidator() {
    }

    public ValidationResult validate(String input) {
        ValidationResult validationResult = super.validate(input);
        if (!validationResult.isValid)
            return validationResult;

        try {
            new URL(input); // does not cover all invalid urls, so we use a regex as well
            String regex = "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
            checkArgument(input.matches(regex), "URL does not match regex");
            return validationResult;
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidUrl"));
        }
    }
}
