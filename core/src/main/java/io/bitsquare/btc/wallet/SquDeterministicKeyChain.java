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
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

class SquDeterministicKeyChain extends DeterministicKeyChain {
    private static final Logger log = LoggerFactory.getLogger(SquDeterministicKeyChain.class);

    // See https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
    // https://github.com/satoshilabs/slips/blob/master/slip-0044.md
    // We use 139 (0x80000000) as coin_type for SQU 
    // TODO register once the token name is fix
    public static final ImmutableList<ChildNumber> BIP44_SQU_ACCOUNT_PATH = ImmutableList.of(
            new ChildNumber(44, true),
            new ChildNumber(139, true),
            ChildNumber.ZERO_HARDENED);

    public SquDeterministicKeyChain(SecureRandom random) {
        super(random);
    }

    public SquDeterministicKeyChain(DeterministicKey accountKey, boolean isFollowingKey) {
        super(accountKey, isFollowingKey);
    }

    public SquDeterministicKeyChain(DeterministicSeed seed, KeyCrypter crypter) {
        super(seed, crypter);
    }

    public SquDeterministicKeyChain(DeterministicSeed seed) {
        super(seed);
    }

    @Override
    protected ImmutableList<ChildNumber> getAccountPath() {
        return BIP44_SQU_ACCOUNT_PATH;
    }

}
