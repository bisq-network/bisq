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
 * {@link AddressValidator} for Base58-encoded Cryptonote addresses.
 *
 * @author Xiphon
 */
public class CryptoNoteAddressValidator implements AddressValidator {

    private final long[] validPrefixes;
    private final boolean validateChecksum;

    public CryptoNoteAddressValidator(boolean validateChecksum, long... validPrefixes) {
        this.validPrefixes = validPrefixes;
        this.validateChecksum = validateChecksum;
    }

    public CryptoNoteAddressValidator(long... validPrefixes) {
        this(true, validPrefixes);
    }

    @Override
    public AddressValidationResult validate(String address) {
        try {
            long prefix = CryptoNoteUtils.MoneroBase58.decodeAddress(address, this.validateChecksum);
            for (long validPrefix : this.validPrefixes) {
                if (prefix == validPrefix) {
                    return AddressValidationResult.validAddress();
                }
            }
            return AddressValidationResult.invalidAddress(String.format("invalid address prefix %x", prefix));
        } catch (Exception e) {
            return AddressValidationResult.invalidStructure();
        }
    }
}
