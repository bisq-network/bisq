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

package bisq.asset;

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.utils.MonetaryFormat;

/**
 * Convenient abstract {@link NetworkParameters} base class providing no-op
 * implementations of all methods that are not required for address validation
 * purposes.
 *
 * @author Chris Beams
 * @since 0.7.0
 */
public abstract class NetworkParametersAdapter extends NetworkParameters {

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }

    @Override
    public void checkDifficultyTransitions(StoredBlock storedPrev, Block next, BlockStore blockStore)
            throws VerificationException {
    }

    @Override
    public Coin getMaxMoney() {
        return null;
    }

    @Override
    public Coin getMinNonDustOutput() {
        return null;
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        return null;
    }

    @Override
    public String getUriScheme() {
        return null;
    }

    @Override
    public boolean hasMaxMoney() {
        return false;
    }

    @Override
    public BitcoinSerializer getSerializer(boolean parseRetain) {
        return null;
    }

    @Override
    public int getProtocolVersionNum(ProtocolVersion version) {
        return 0;
    }
}
