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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;

import java.security.SecureRandom;

class BitsquareKeyChainGroup extends KeyChainGroup {
    private final boolean useBitcoinDeterministicKeyChain;

    public boolean isUseBitcoinDeterministicKeyChain() {
        return useBitcoinDeterministicKeyChain;
    }

    public BitsquareKeyChainGroup(NetworkParameters params, boolean useBitcoinDeterministicKeyChain) {
        super(params);
        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
    }

    public BitsquareKeyChainGroup(NetworkParameters params, DeterministicSeed seed, boolean useBitcoinDeterministicKeyChain) {
        super(params, seed);
        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
    }

    @Override
    public void createAndActivateNewHDChain() {
        DeterministicKeyChain chain = useBitcoinDeterministicKeyChain ?
                new BitcoinDeterministicKeyChain(new SecureRandom()) :
                new SquDeterministicKeyChain(new SecureRandom());
        addAndActivateHDChain(chain);
    }
}
