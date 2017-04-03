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

package io.bisq.core.btc.wallet;

import com.google.common.collect.Sets;
import org.bitcoinj.core.*;
import org.bitcoinj.params.RegTestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches any of our addresses.
 */
class BtcCoinSelector extends BisqDefaultCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(BtcCoinSelector.class);

    private final NetworkParameters params;
    private final Set<Address> addresses;
    private final boolean permitForeignPendingTx;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    BtcCoinSelector(NetworkParameters params, Set<Address> addresses, boolean permitForeignPendingTx) {
        this.params = params;
        this.addresses = addresses;
        this.permitForeignPendingTx = permitForeignPendingTx;
    }

    BtcCoinSelector(NetworkParameters params, Set<Address> addresses) {
        this(params, addresses, true);
    }

    BtcCoinSelector(NetworkParameters params, Address address, boolean permitForeignPendingTx) {
        this(params, Sets.newHashSet(address), permitForeignPendingTx);
    }

    BtcCoinSelector(NetworkParameters params, Address address) {
        this(params, Sets.newHashSet(address), true);
    }

    @Override
    protected boolean isSelectable(Transaction tx) {
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        boolean isConfirmed = type.equals(TransactionConfidence.ConfidenceType.BUILDING);
        boolean isPending = type.equals(TransactionConfidence.ConfidenceType.PENDING);
        boolean isOwnTxAndPending = isPending &&
                confidence.getSource().equals(TransactionConfidence.Source.SELF) &&
                // In regtest mode we expect to have only one peer, so we won't see transactions propagate.
                // TODO: The value 1 below dates from a time when transactions we broadcast *to* were counted, set to 0
                // TODO check with local BTC mainnet core node (1 connection)
                (confidence.numBroadcastPeers() > 1 || tx.getParams() == RegTestParams.get());
        return isConfirmed || (permitForeignPendingTx && isPending) || isOwnTxAndPending;
    }

    @Override
    protected boolean selectOutput(TransactionOutput output) {
        if (WalletUtils.isOutputScriptConvertableToAddress(output)) {
            Address address = WalletUtils.getAddressFromOutput(output);

            boolean matchesAddress = addresses.contains(address);
            if (!matchesAddress)
                log.trace("addresses not containing address " +
                        addresses + " / " + address.toString());

            return matchesAddress;
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }
    }
}
