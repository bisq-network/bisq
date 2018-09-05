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
 * {@link AddressValidator} for use when no more specific address validation function is
 * available. This implementation only checks that a given address is non-null and
 * non-empty; it exists for legacy purposes and is deprecated to indicate that new
 * {@link Asset} implementations should NOT use it, but should rather provide their own
 * {@link AddressValidator} implementation.
 *
 * @author Chris Beams
 * @author Bernard Labno
 * @since 0.7.0
 */
@Deprecated
public class DefaultAddressValidator implements AddressValidator {

    @Override
    public AddressValidationResult validate(String address) {
        if (address == null || address.length() == 0)
            return AddressValidationResult.invalidAddress("Address may not be empty");

        return AddressValidationResult.validAddress();
    }
}
