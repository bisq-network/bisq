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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.Arrays;

public class Cryptonite extends Coin {

    public Cryptonite() {
        super("Cryptonite", "XCN", new CryptoniteAddressValidator());
    }


    public static class CryptoniteAddressValidator implements AddressValidator {

        private final static String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

        @Override
        public AddressValidationResult validate(String address) {
            // https://bitcointalk.org/index.php?topic=1801595
            if (address.length() != 34)
                return AddressValidationResult.invalidAddress("XCN_Addr_Invalid: Length must be 34!");

            if (!address.startsWith("C"))
                return AddressValidationResult.invalidAddress("XCN_Addr_Invalid: must start with 'C'!");

            byte[] decoded = decodeBase58(address);
            if (decoded == null)
                return AddressValidationResult.invalidAddress("XCN_Addr_Invalid: Base58 decoder error!");

            byte[] hash = getSha256(decoded, 21, 2);
            if (hash == null || !Arrays.equals(Arrays.copyOfRange(hash, 0, 4), Arrays.copyOfRange(decoded, 21, 25)))
                return AddressValidationResult.invalidAddress("XCN_Addr_Invalid: Checksum error!");

            return AddressValidationResult.validAddress();
        }

        private static byte[] decodeBase58(String input) {
            byte[] output = new byte[25];
            for (int i = 0; i < input.length(); i++) {
                char t = input.charAt(i);

                int p = ALPHABET.indexOf(t);
                if (p == -1)
                    return null;
                for (int j = 25 - 1; j >= 0; j--, p /= 256) {
                    p += 58 * (output[j] & 0xFF);
                    output[j] = (byte) (p % 256);
                }
                if (p != 0)
                    return null;
            }

            return output;
        }

        private static byte[] getSha256(byte[] data, int len, int recursion) {
            if (recursion == 0)
                return data;

            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(Arrays.copyOfRange(data, 0, len));
                return getSha256(md.digest(), 32, recursion - 1);
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        }
    }
}
