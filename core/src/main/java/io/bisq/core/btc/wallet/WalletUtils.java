/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.btc.wallet;

import io.bisq.core.btc.BitcoinNetwork;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;

import javax.annotation.Nullable;

public class WalletUtils {
    private static BitcoinNetwork bitcoinNetwork;

    public static NetworkParameters getParameters() {
        return getBitcoinNetwork().getParameters();
    }

    public static boolean isOutputScriptConvertableToAddress(TransactionOutput output) {
        return output.getScriptPubKey().isSentToAddress() ||
                output.getScriptPubKey().isPayToScriptHash();
    }

    @Nullable
    public static Address getAddressFromOutput(TransactionOutput output) {
        return isOutputScriptConvertableToAddress(output) ?
                output.getScriptPubKey().getToAddress(getParameters()) : null;
    }

    @Nullable
    public static String getAddressStringFromOutput(TransactionOutput output) {
        return isOutputScriptConvertableToAddress(output) ?
                output.getScriptPubKey().getToAddress(getParameters()).toString() : null;
    }

    public static void setBitcoinNetwork(BitcoinNetwork bitcoinNetwork) {
        WalletUtils.bitcoinNetwork = bitcoinNetwork;
    }

    public static BitcoinNetwork getBitcoinNetwork() {
        return bitcoinNetwork;
    }

   /* public static boolean isRegTest() {
        final Preferences preferences = Preferences.INSTANCE;
        return preferences != null ? preferences.getBitcoinNetwork().equals(BitcoinNetwork.REGTEST) : false;
    }*/
}
