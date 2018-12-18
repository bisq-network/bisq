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

class BtcDeterministicKeyChain extends DeterministicKeyChain {
    private static final Logger log = LoggerFactory.getLogger(BtcDeterministicKeyChain.class);

    // See https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
    // https://github.com/satoshilabs/slips/blob/master/slip-0044.md
    // We use 0 (0x80000000) as coin_type for BTC
    // m / purpose' / coin_type' / account' / change / address_index
    public static final ImmutableList<ChildNumber> BIP44_BTC_ACCOUNT_PATH = ImmutableList.of(
            new ChildNumber(44, true),
            new ChildNumber(0, true),
            ChildNumber.ZERO_HARDENED);

    public BtcDeterministicKeyChain(SecureRandom random) {
        super(random);
    }

    public BtcDeterministicKeyChain(DeterministicKey accountKey, boolean isFollowingKey) {
        super(accountKey, isFollowingKey);
    }

    public BtcDeterministicKeyChain(DeterministicSeed seed, KeyCrypter crypter) {
        super(seed, crypter);
    }

    public BtcDeterministicKeyChain(DeterministicSeed seed) {
        super(seed);
    }

    @Override
    public DeterministicKeyChain toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
        return new BtcDeterministicKeyChain(keyCrypter, aesKey, this);
    }

    @Override
    protected DeterministicKeyChain makeKeyChainFromSeed(DeterministicSeed seed) {
        return new BtcDeterministicKeyChain(seed);
    }

    protected BtcDeterministicKeyChain(KeyCrypter crypter, KeyParameter aesKey, DeterministicKeyChain chain) {
        super(crypter, aesKey, chain);
    }

    @Override
    protected ImmutableList<ChildNumber> getAccountPath() {
        return BIP44_BTC_ACCOUNT_PATH;
    }

}
