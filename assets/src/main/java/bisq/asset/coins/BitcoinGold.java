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

import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;
import bisq.asset.NetworkParametersAdapter;

import org.bitcoinj.core.Utils;

public class BitcoinGold extends Coin {

    public BitcoinGold() {
        super("Bitcoin Gold", "BTG", new Base58BitcoinAddressValidator(new BitcoinGoldParams()));
    }


    private static class BitcoinGoldParams extends NetworkParametersAdapter {

        public BitcoinGoldParams() {
            interval = INTERVAL;
            targetTimespan = TARGET_TIMESPAN;
            maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
            dumpedPrivateKeyHeader = 128;

            // Address format is different to BTC, rest is the same
            addressHeader = 38;
            p2shHeader = 23;

            acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
            port = 8333;
            packetMagic = 0xf9beb4d9L;
            bip32HeaderPub = 0x0488B21E; //The 4 byte header that serializes in base58 to "xpub".
            bip32HeaderPriv = 0x0488ADE4; //The 4 byte header that serializes in base58 to "xprv"

            id = ID_MAINNET;
        }
    }
}
