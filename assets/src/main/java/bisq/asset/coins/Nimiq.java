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

public class Nimiq extends Coin {
    public Nimiq() {
        super("Nimiq", "NIM", new NimiqAddressValidator());
    }

    /**
     * Nimiq Address validation code derived from the original JavaScript implementation
     */
    public static class NimiqAddressValidator implements AddressValidator {
        @Override
        public AddressValidationResult validate(String address) {
            address = address.replace(" ", "");
            if (!address.matches("NQ[0-9]{2}[0-9A-HJ-NP-VXY]{32}")) {
                return AddressValidationResult.invalidStructure();
            }
            if (ibanCheck(address.substring(4) + address.substring(0, 4)) != 1) {
                return AddressValidationResult.invalidAddress("Checksum invalid");
            }
            return AddressValidationResult.validAddress();
        }

        private int ibanCheck(String str) {
            StringBuilder sb = new StringBuilder();
            for (char c : str.toUpperCase().toCharArray()) {
                if (c >= 48 && c <= 57) {
                    sb.append(c);
                } else {
                    sb.append(Integer.toString(c - 55));
                }
            }
            String num = sb.toString();
            int tmp = 0;
            for (int i = 0; i < Math.ceil(num.length() / 6.0); i++) {
                tmp = Integer.parseInt(tmp + num.substring(i * 6, Math.min((i + 1) * 6, num.length()))) % 97;
            }

            return tmp;
        }
    }
}
