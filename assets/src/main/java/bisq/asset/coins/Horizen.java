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

package bisq.asset.coins;

import bisq.asset.AddressValidationResult;
import bisq.asset.AddressValidator;
import bisq.asset.Coin;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;

public class Horizen extends Coin {

    public Horizen() {
        super("Horizen", "ZEN", new HorizenAddressValidator());
    }


    public static class HorizenAddressValidator implements AddressValidator {

        @Override
        public AddressValidationResult validate(String address) {
            byte[] byteAddress;
            try {
                // Get the non Base58 form of the address and the bytecode of the first two bytes
                byteAddress = Base58.decodeChecked(address);
            } catch (AddressFormatException e) {
                // Unhandled Exception (probably a checksum error)
                return AddressValidationResult.invalidAddress(e);
            }
            int version0 = byteAddress[0] & 0xFF;
            int version1 = byteAddress[1] & 0xFF;

            // We only support public ("zn" (0x20,0x89), "t1" (0x1C,0xB8))
            // and multisig ("zs" (0x20,0x96), "t3" (0x1C,0xBD)) addresses

            // Fail for private addresses
            if (version0 == 0x16 && version1 == 0x9A)
                // Address starts with "zc"
                return AddressValidationResult.invalidAddress("", "validation.altcoin.zAddressesNotSupported");

            if (version0 == 0x1C && (version1 == 0xB8 || version1 == 0xBD))
                // "t1" or "t3" address
                return AddressValidationResult.validAddress();

            if (version0 == 0x20 && (version1 == 0x89 || version1 == 0x96))
                // "zn" or "zs" address
                return AddressValidationResult.validAddress();

            // Unknown Type
            return AddressValidationResult.invalidStructure();
        }
    }
}
