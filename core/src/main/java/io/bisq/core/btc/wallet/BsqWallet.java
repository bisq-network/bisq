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

import org.bitcoinj.core.*;
import org.bitcoinj.wallet.KeyChainGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class BsqWallet extends Wallet {
    private static final Logger log = LoggerFactory.getLogger(BsqWallet.class);

    public BsqWallet(NetworkParameters params, KeyChainGroup keyChainGroup) {
        super(params, keyChainGroup);
    }

    /**
     * Returns the spendable candidates from the {@link UTXOProvider} based on keys that the wallet contains.
     *
     * @return The list of candidates.
     */
    @Override
    protected List<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(boolean excludeImmatureCoinbases) {
        checkState(lock.isHeldByCurrentThread());
        UTXOProvider utxoProvider = checkNotNull(vUTXOProvider, "No UTXO provider has been set");
        // We might get duplicate outputs from the provider and from our pending tx outputs
        // To avoid duplicate entries we use a set.
        Set<TransactionOutput> candidates = new HashSet<>();
        try {
            int chainHeight = utxoProvider.getChainHeadHeight();
            for (UTXO output : getStoredOutputsFromUTXOProvider()) {
                boolean coinbase = output.isCoinbase();
                int depth = chainHeight - output.getHeight() + 1; // the current depth of the output (1 = same as head).
                // Do not try and spend coinbases that were mined too recently, the protocol forbids it.
                if (!excludeImmatureCoinbases || !coinbase || depth >= params.getSpendableCoinbaseDepth()) {
                    candidates.add(new FreeStandingTransactionOutput(params, output, chainHeight));
                }
            }
        } catch (UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }
        // We need to handle the pending transactions that we know about.
        for (Transaction tx : pending.values()) {
            // Remove the spent outputs.
            for (TransactionInput input : tx.getInputs()) {
                TransactionOutput connectedOutput = input.getConnectedOutput();
                if (connectedOutput != null && connectedOutput.isMine(this)) {
                    candidates.remove(connectedOutput);
                }
            }
            // Add change outputs. Do not try and spend coinbases that were mined too recently, the protocol forbids it.

            // We might get outputs from pending tx which we already got form the UTXP provider. 
            // As we use a set it will not lead to duplicate entries.
            if (!excludeImmatureCoinbases || tx.isMature()) {
                candidates.addAll(tx.getOutputs().stream()
                        .filter(output -> output.isAvailableForSpending() && output.isMine(this))
                        .collect(Collectors.toList()));
            }
        }
        return new ArrayList<>(candidates);
    }
}
