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

package bisq.core.btc.setup;

import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DefaultKeyChainFactory;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroupStructure;
import org.bitcoinj.wallet.Protos;

import com.google.common.collect.ImmutableList;

/**
 * Hack to convert bitcoinj 0.14 wallets to bitcoinj 0.15 format.
 *
 * This code is required to be executed only once per user (actually twice, for btc and bsq wallets).
 * Once all users using bitcoinj 0.14 wallets have executed this code, this class will be no longer needed.
 *
 * Since that is almost impossible to guarantee, this hack will stay until we decide to don't be
 * backwards compatible with pre bitcoinj 0.15 wallets.
 * In that scenario, users will have to migrate using this procedure:
 * 1) Run pre bitcoinj 0.15 bisq and copy their seed words on a piece of paper.
 * 2) Run post bitcoinj 0.15 bisq and use recover from seed.
 * */
public class BisqKeyChainFactory extends DefaultKeyChainFactory {

    private boolean isBsqWallet;

    public BisqKeyChainFactory(boolean isBsqWallet) {
        this.isBsqWallet = isBsqWallet;
    }

    @Override
    public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried, Script.ScriptType outputScriptType, ImmutableList<ChildNumber> accountPath) {
        ImmutableList<ChildNumber> maybeUpdatedAccountPath = accountPath;
        if (DeterministicKeyChain.ACCOUNT_ZERO_PATH.equals(accountPath)) {
            // This is a bitcoinj 0.14 wallet that has no account path in the serialized mnemonic
            KeyChainGroupStructure structure = new BisqKeyChainGroupStructure(isBsqWallet);
            maybeUpdatedAccountPath = structure.accountPathFor(outputScriptType);
        }

        return super.makeKeyChain(key, firstSubKey, seed, crypter, isMarried, outputScriptType, maybeUpdatedAccountPath);
    }

    @Override
    public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey, boolean isFollowingKey, boolean isMarried, Script.ScriptType outputScriptType) {
        throw new UnsupportedOperationException("Bisq is not supposed to use this");
    }

    @Override
    public DeterministicKeyChain makeSpendingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey, boolean isMarried, Script.ScriptType outputScriptType) {
        throw new UnsupportedOperationException("Bisq is not supposed to use this");
    }
}
