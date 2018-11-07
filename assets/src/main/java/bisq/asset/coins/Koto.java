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

public class Koto extends Coin {

    public Koto() {
        super("Koto", "KOTO", new KotoAddressValidator());
    }


    public static class KotoAddressValidator implements AddressValidator {

        private final static String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

        @Override
        public AddressValidationResult validate(String address) {
            if (address.startsWith("z"))
                return AddressValidationResult.invalidAddress("KOTO_Addr_Invalid: z Address not supported!");
            if (address.length() != 35)
                return AddressValidationResult.invalidAddress("KOTO_Addr_Invalid: Length must be 35!");
            if (!address.startsWith("k1") && !address.startsWith("jz"))
                return AddressValidationResult.invalidAddress("KOTO_Addr_Invalid: must start with 'k1' or 'jz'!");
            byte[] decoded = decodeBase58(address, 58, 26);
            if (decoded == null)
                return AddressValidationResult.invalidAddress("KOTO_Addr_Invalid: Base58 decoder error!");

            byte[] hash = getSha256(decoded, 0, 22, 2);
            if (hash == null || !Arrays.equals(Arrays.copyOfRange(hash, 0, 4), Arrays.copyOfRange(decoded, 22, 26)))
                return AddressValidationResult.invalidAddress("KOTO_Addr_Invalid: Checksum error!");

            return AddressValidationResult.validAddress();

        }

        private static byte[] decodeBase58(String input, int base, int len) {
            byte[] output = new byte[len];
            for (int i = 0; i < input.length(); i++) {
                char t = input.charAt(i);

                int p = ALPHABET.indexOf(t);
                if (p == -1)
                    return null;
                for (int j = len - 1; j >= 0; j--, p /= 256) {
                    p += base * (output[j] & 0xFF);
                    output[j] = (byte) (p % 256);
                }
                if (p != 0)
                    return null;
            }

            return output;
        }

        private static byte[] getSha256(byte[] data, int start, int len, int recursion) {
            if (recursion == 0)
                return data;

            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(Arrays.copyOfRange(data, start, start + len));
                return getSha256(md.digest(), 0, 32, recursion - 1);
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        }
    }
}
