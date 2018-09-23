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
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;

import com.google.common.collect.ImmutableList;

import org.spongycastle.crypto.params.KeyParameter;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BisqDeterministicKeyChain extends DeterministicKeyChain {
    private static final Logger log = LoggerFactory.getLogger(BisqDeterministicKeyChain.class);

    // See https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
    // https://github.com/satoshilabs/slips/blob/master/slip-0044.md
    // We have registered 142 (0x8000008E) as coin_type for BSQ
    public static final ImmutableList<ChildNumber> BIP44_BSQ_ACCOUNT_PATH = ImmutableList.of(
            new ChildNumber(44, true),
            new ChildNumber(142, true),
            ChildNumber.ZERO_HARDENED);

    public BisqDeterministicKeyChain(SecureRandom random) {
        super(random);
    }

    public BisqDeterministicKeyChain(DeterministicKey accountKey, boolean isFollowingKey) {
        super(accountKey, isFollowingKey);
    }

    public BisqDeterministicKeyChain(DeterministicSeed seed, KeyCrypter crypter) {
        super(seed, crypter);
    }

    public BisqDeterministicKeyChain(DeterministicSeed seed) {
        super(seed);
    }


    @Override
    public DeterministicKeyChain toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
        return new BisqDeterministicKeyChain(keyCrypter, aesKey, this);
    }

    protected DeterministicKeyChain makeKeyChainFromSeed(DeterministicSeed seed) {
        return new BisqDeterministicKeyChain(seed);
    }

    protected BisqDeterministicKeyChain(KeyCrypter crypter, KeyParameter aesKey, DeterministicKeyChain chain) {
        super(crypter, aesKey, chain);
    }

    @Override
    protected ImmutableList<ChildNumber> getAccountPath() {
        return BIP44_BSQ_ACCOUNT_PATH;
    }

}
