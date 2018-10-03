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
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainFactory;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;

import com.google.common.collect.ImmutableList;

class BisqKeyChainFactory implements KeyChainFactory {
    private final boolean useBitcoinDeterministicKeyChain;

    public BisqKeyChainFactory(boolean useBitcoinDeterministicKeyChain) {
        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
    }

    @Override
    public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried) {
        return useBitcoinDeterministicKeyChain ? new BtcDeterministicKeyChain(seed, crypter) : new BisqDeterministicKeyChain(seed, crypter);
    }

    @Override
    public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey,
                                                      boolean isFollowingKey, boolean isMarried) throws UnreadableWalletException {
        ImmutableList<ChildNumber> accountPath = useBitcoinDeterministicKeyChain ? BtcDeterministicKeyChain.BIP44_BTC_ACCOUNT_PATH : BisqDeterministicKeyChain.BIP44_BSQ_ACCOUNT_PATH;
        if (!accountKey.getPath().equals(accountPath))
            throw new UnreadableWalletException("Expecting account key but found key with path: " +
                    HDUtils.formatPath(accountKey.getPath()));
        return useBitcoinDeterministicKeyChain ? new BtcDeterministicKeyChain(accountKey, isFollowingKey) : new BisqDeterministicKeyChain(accountKey, isFollowingKey);
    }
}
