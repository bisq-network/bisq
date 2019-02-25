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

import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Mile extends Coin {

    public Mile() {
        super("Mile", "MILE", new MileAddressValidator());
    }


    /**
     * Mile address - base58(32 bytes of public key + 4 bytes of crc32)
     */
    public static class MileAddressValidator implements AddressValidator {
        public MileAddressValidator() {
        }

        @Override
        public AddressValidationResult validate(String address) {
            byte[] decoded;

            try {
                decoded = Base58.decode(address);
            } catch (AddressFormatException e) {
                return AddressValidationResult.invalidAddress(e.getMessage());
            }
            if (decoded.length != 32 + 4)
                return AddressValidationResult.invalidAddress("Invalid address");

            byte[] data = Arrays.copyOfRange(decoded, 0, decoded.length - 4);
            byte[] addrChecksum = Arrays.copyOfRange(decoded, decoded.length - 4, decoded.length);

            Checksum checksum = new CRC32();
            checksum.update(data, 0, data.length);
            long checksumValue = checksum.getValue();

            if ((byte)(checksumValue & 0xff) != addrChecksum[0] ||
                    (byte)((checksumValue >> 8) & 0xff) != addrChecksum[1] ||
                    (byte)((checksumValue >> 16) & 0xff) != addrChecksum[2] ||
                    (byte)((checksumValue >> 24) & 0xff) != addrChecksum[3])
            {
                return AddressValidationResult.invalidAddress("Invalid address checksum");
            }

            return AddressValidationResult.validAddress();
        }
    }
}
