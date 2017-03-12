/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.btc.wallet;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.KeyChainGroup;

import java.security.SecureRandom;

class BitsquareKeyChainGroup extends KeyChainGroup {
    private final boolean useBitcoinDeterministicKeyChain;
    private int lookaheadSize;

    public boolean isUseBitcoinDeterministicKeyChain() {
        return useBitcoinDeterministicKeyChain;
    }

    public BitsquareKeyChainGroup(NetworkParameters params, boolean useBitcoinDeterministicKeyChain, int lookaheadSize) {
        super(params);
        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
        this.lookaheadSize = lookaheadSize;
    }

    public BitsquareKeyChainGroup(NetworkParameters params, DeterministicKeyChain chain, boolean useBitcoinDeterministicKeyChain, int lookaheadSize) {
        super(params, chain);

        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
        this.lookaheadSize = lookaheadSize;
    }

    @Override
    public void setLookaheadSize(int lookaheadSize) {
        super.setLookaheadSize(lookaheadSize);
    }

    @Override
    public void createAndActivateNewHDChain() {
        DeterministicKeyChain chain = useBitcoinDeterministicKeyChain ? new BtcDeterministicKeyChain(new SecureRandom()) : new BsqDeterministicKeyChain(new SecureRandom());
        chain.setLookaheadSize(lookaheadSize);
        addAndActivateHDChain(chain);
    }
}
