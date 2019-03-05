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

package bisq.core.btc.wallet;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.TransactionOutput;

import com.google.common.collect.Sets;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches any of our addresses.
 */
class BtcCoinSelector extends BisqDefaultCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(BtcCoinSelector.class);

    private final Set<Address> addresses;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    BtcCoinSelector(Set<Address> addresses, boolean permitForeignPendingTx) {
        super(permitForeignPendingTx);
        this.addresses = addresses;
    }

    BtcCoinSelector(Set<Address> addresses) {
        this(addresses, true);
    }

    BtcCoinSelector(Address address, @SuppressWarnings("SameParameterValue") boolean permitForeignPendingTx) {
        this(Sets.newHashSet(address), permitForeignPendingTx);
    }

    BtcCoinSelector(Address address) {
        this(Sets.newHashSet(address), true);
    }

    @Override
    protected boolean isTxOutputSpendable(TransactionOutput output) {
        if (WalletService.isOutputScriptConvertibleToAddress(output)) {
            Address address = WalletService.getAddressFromOutput(output);
            return addresses.contains(address);
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }
    }
}
