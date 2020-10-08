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

import lombok.extern.slf4j.Slf4j;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches any of our addresses.
 */
@Slf4j
class BtcCoinSelector extends BisqDefaultCoinSelector {
    private final Set<Address> addresses;
    private final long ignoreDustThreshold;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BtcCoinSelector(Set<Address> addresses, long ignoreDustThreshold, boolean permitForeignPendingTx) {
        super(permitForeignPendingTx);
        this.addresses = addresses;
        this.ignoreDustThreshold = ignoreDustThreshold;
    }

    BtcCoinSelector(Set<Address> addresses, long ignoreDustThreshold) {
        this(addresses, ignoreDustThreshold, true);
    }

    BtcCoinSelector(Address address, long ignoreDustThreshold, @SuppressWarnings("SameParameterValue") boolean permitForeignPendingTx) {
        this(Sets.newHashSet(address), ignoreDustThreshold, permitForeignPendingTx);
    }

    BtcCoinSelector(Address address, long ignoreDustThreshold) {
        this(Sets.newHashSet(address), ignoreDustThreshold, true);
    }

    @Override
    protected boolean isTxOutputSpendable(TransactionOutput output) {
        if (WalletService.isOutputScriptConvertibleToAddress(output)) {
            Address address = WalletService.getAddressFromOutput(output);
            return addresses.contains(address);
        } else {
            log.warn("transactionOutput.getScriptPubKey() is not P2PKH nor P2SH nor P2WH");
            return false;
        }
    }

    // We ignore utxos which are considered dust attacks for spying on users' wallets.
    // The ignoreDustThreshold value is set in the preferences. If not set we use default non dust
    // value of 546 sat.
    @Override
    protected boolean isDustAttackUtxo(TransactionOutput output) {
        return output.getValue().value < ignoreDustThreshold;
    }
}
