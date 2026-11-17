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

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;

/**
 * Validates a Solana address: the Base58 encoding of a 32 byte public key. SPL tokens are
 * sent to the address of the Solana account which owns the token account.
 * <p>
 * A Solana address has no checksum, so a mistyped address of the right length passes.
 */
public class SolanaAddressValidator implements AddressValidator {
    private static final int PUBLIC_KEY_LENGTH = 32;

    @Override
    public AddressValidationResult validate(String address) {
        byte[] decoded;
        try {
            decoded = Base58.decode(address);
        } catch (AddressFormatException e) {
            return AddressValidationResult.invalidAddress(e);
        }
        if (decoded.length != PUBLIC_KEY_LENGTH) {
            return AddressValidationResult.invalidStructure();
        }
        return AddressValidationResult.validAddress();
    }
}
