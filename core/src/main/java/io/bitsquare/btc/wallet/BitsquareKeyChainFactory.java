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

package io.bitsquare.btc.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainFactory;
import org.bitcoinj.wallet.Protos;

class BitsquareKeyChainFactory implements KeyChainFactory {
    private final boolean useBitcoinDeterministicKeyChain;

    public BitsquareKeyChainFactory(boolean useBitcoinDeterministicKeyChain) {
        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
    }

    @Override
    public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried) {
        return useBitcoinDeterministicKeyChain ? new BitcoinDeterministicKeyChain(seed, crypter) : new SquDeterministicKeyChain(seed, crypter);
    }

    @Override
    public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey,
                                                      boolean isFollowingKey, boolean isMarried) throws UnreadableWalletException {
        ImmutableList<ChildNumber> accountPath = useBitcoinDeterministicKeyChain ? BitcoinDeterministicKeyChain.BIP44_BTC_ACCOUNT_PATH : SquDeterministicKeyChain.BIP44_SQU_ACCOUNT_PATH;
        if (!accountKey.getPath().equals(accountPath))
            throw new UnreadableWalletException("Expecting account key but found key with path: " +
                    HDUtils.formatPath(accountKey.getPath()));
        return useBitcoinDeterministicKeyChain ? new BitcoinDeterministicKeyChain(accountKey, isFollowingKey) : new SquDeterministicKeyChain(accountKey, isFollowingKey);
    }
}
