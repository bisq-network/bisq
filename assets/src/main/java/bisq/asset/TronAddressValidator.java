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
 * Validates a Tron address: Base58Check of the version byte 0x41 and a 20 byte account hash,
 * which gives 34 characters starting with 'T'. TRC-20 tokens use the address of the Tron
 * account.
 */
public class TronAddressValidator extends Base58AddressValidator {

    public TronAddressValidator() {
        super(new TronMainNetParams());
    }

    private static class TronMainNetParams extends NetworkParametersAdapter {

        TronMainNetParams() {
            this.addressHeader = 0x41;
            this.p2shHeader = 0x41;
        }
    }
}
