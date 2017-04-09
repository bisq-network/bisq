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

import io.bisq.core.dao.blockchain.BsqUTXO;
import io.bisq.core.dao.blockchain.BsqUTXOMap;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our address.
 */
class BsqCoinSelector extends BisqDefaultCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(BsqCoinSelector.class);

    private final Map<String, Set<BsqUTXO>> utxoSetByAddressMap = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqCoinSelector(boolean permitForeignPendingTx) {
        super(permitForeignPendingTx);
    }

    public void setUtxoMap(BsqUTXOMap bsqUTXOMap) {
        bsqUTXOMap.values().stream().forEach(utxo -> {
            String address = utxo.getAddress();
            if (!utxoSetByAddressMap.containsKey(address))
                utxoSetByAddressMap.put(address, new HashSet<>());

            utxoSetByAddressMap.get(address).add(utxo);
        });
    }

    @Override
    protected boolean isTxOutputSpendable(TransactionOutput output) {
        if (WalletUtils.isOutputScriptConvertableToAddress(output)) {
            return utxoSetByAddressMap.containsKey(WalletUtils.getAddressStringFromOutput(output));
        } else {
            log.warn("output.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }
    }
}
