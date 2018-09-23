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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.KeyChainGroup;

import java.security.SecureRandom;

class BisqKeyChainGroup extends KeyChainGroup {
    private final boolean useBitcoinDeterministicKeyChain;

    public boolean isUseBitcoinDeterministicKeyChain() {
        return useBitcoinDeterministicKeyChain;
    }

    public BisqKeyChainGroup(NetworkParameters params, @SuppressWarnings("SameParameterValue") boolean useBitcoinDeterministicKeyChain) {
        super(params);
        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
    }

    public BisqKeyChainGroup(NetworkParameters params, DeterministicKeyChain chain, boolean useBitcoinDeterministicKeyChain) {
        super(params, chain);

        this.useBitcoinDeterministicKeyChain = useBitcoinDeterministicKeyChain;
    }

    @Override
    public void createAndActivateNewHDChain() {
        DeterministicKeyChain chain = useBitcoinDeterministicKeyChain ? new BtcDeterministicKeyChain(new SecureRandom()) : new BisqDeterministicKeyChain(new SecureRandom());
        addAndActivateHDChain(chain);
    }
}
