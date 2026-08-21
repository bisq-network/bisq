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

package bisq.asset;

/**
 * Validates an {@link Asset} address against a given regular expression.
 *
 * @author Chris Beams
 * @since 0.7.0
 */
public class RegexAddressValidator implements AddressValidator {

    private final String regex;
    private final String errorMessageI18nKey;

    public RegexAddressValidator(String regex) {
        this(regex, null);
    }

    public RegexAddressValidator(String regex, String errorMessageI18nKey) {
        this.regex = regex;
        this.errorMessageI18nKey = errorMessageI18nKey;
    }

    @Override
    public AddressValidationResult validate(String address) {
        if (!address.matches(regex))
            if (errorMessageI18nKey == null) return AddressValidationResult.invalidStructure();
            else return AddressValidationResult.invalidAddress("", errorMessageI18nKey);

        return AddressValidationResult.validAddress();
    }
}
