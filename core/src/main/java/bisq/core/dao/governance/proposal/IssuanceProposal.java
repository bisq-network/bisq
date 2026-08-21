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

package bisq.core.dao.governance.proposal;

import bisq.common.config.Config;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

/**
 * Interface for proposals which can lead to new BSQ issuance
 */
public interface IssuanceProposal {
    Coin getRequestedBsq();

    String getBsqAddress();

    String getTxId();

    default Address getAddress() throws AddressFormatException {
        // Remove leading 'B'
        String underlyingBtcAddress = getBsqAddress().substring(1);
        return Address.fromString(Config.baseCurrencyNetworkParameters(), underlyingBtcAddress);
    }
}
