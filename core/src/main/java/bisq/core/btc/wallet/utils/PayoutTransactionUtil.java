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

package bisq.core.btc.wallet.utils;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import com.google.common.collect.ImmutableList;

import java.util.List;

public final class PayoutTransactionUtil {
    private PayoutTransactionUtil() {
    }

    public static Script get2of2MultiSigRedeemScript(byte[] buyerPubKey, byte[] sellerPubKey) {
        ECKey buyerKey = ECKey.fromPublicOnly(buyerPubKey);
        ECKey sellerKey = ECKey.fromPublicOnly(sellerPubKey);
        // Take care of sorting! Need to reverse to the order we use normally (buyer, seller)
        List<ECKey> keys = ImmutableList.of(sellerKey, buyerKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }
}
