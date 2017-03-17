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
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
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

    private final boolean permitForeignPendingTx;

    private Map<Script, Set<BsqUTXO>> utxoSetByScriptMap = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqCoinSelector(boolean permitForeignPendingTx) {
        this.permitForeignPendingTx = permitForeignPendingTx;
    }

    public void setUtxoSet(Set<BsqUTXO> utxoSet) {
        utxoSet.stream().forEach(utxo -> {
            Script script = utxo.getScript();
            if (!utxoSetByScriptMap.containsKey(script))
                utxoSetByScriptMap.put(script, new HashSet<>());

            utxoSetByScriptMap.get(script).add(utxo);
        });
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
    protected boolean selectOutput(TransactionOutput transactionOutput) {
        Script scriptPubKey = transactionOutput.getScriptPubKey();
        if (scriptPubKey.isSentToAddress() || scriptPubKey.isPayToScriptHash()) {
            return utxoSetByScriptMap.containsKey(scriptPubKey);
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }
    }
}
