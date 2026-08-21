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

package bisq.core.btc.wallet;

import bisq.core.crypto.LowRSigningKey;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.RedeemData;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class LowRSigningKeyBag implements KeyBag {
    private final KeyBag target;

    public LowRSigningKeyBag(KeyBag target) {
        this.target = target;
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubKeyHash(byte[] pubKeyHash, @Nullable Script.ScriptType scriptType) {
        return LowRSigningKey.from(target.findKeyFromPubKeyHash(pubKeyHash, scriptType));
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubKey(byte[] pubKey) {
        return LowRSigningKey.from(target.findKeyFromPubKey(pubKey));
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
        RedeemData redeemData = target.findRedeemDataFromScriptHash(scriptHash);
        if (redeemData == null) {
            return null;
        }
        List<ECKey> lowRKeys = redeemData.keys.stream().map(LowRSigningKey::from).collect(Collectors.toList());
        return RedeemData.of(lowRKeys, redeemData.redeemScript);
    }
}
