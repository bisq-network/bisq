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

package io.bisq.dao.tokens;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionParser {
    private static final Logger log = LoggerFactory.getLogger(TransactionParser.class);

    private String genesisTxId;
    private TxService txService;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TransactionParser(String genesisTxId, TxService txService) {
        this.genesisTxId = genesisTxId;
        this.txService = txService;
    }

    public Tx getTx(String txId) {
        return txService.getTx(txId);
    }

    public void applyIsTokenForAllOutputs(Tx parentTx) {
        if (parentTx.id.equals(genesisTxId)) {
            // direct output from genesisTx 
            parentTx.outputs.stream().forEach(e -> e.isToken = true);
        } else {
            // we are not a direct output so we check if our inputs are valid and sufficiently funded with tokens
            int accumulatedTokenInputValue = 0;
            for (TxInput input : parentTx.inputs) {
                if (isValidInput(input)) {
                    accumulatedTokenInputValue += input.value;
                }
            }
            log.debug("accumulatedTokenInputValue " + accumulatedTokenInputValue);
            List<TxOutput> outputs = parentTx.outputs;
            for (int i = 0; i < outputs.size(); i++) {
                TxOutput out = outputs.get(i);
                log.debug("index {}, out.value {}, available input value {}", i, out.value, accumulatedTokenInputValue);
                accumulatedTokenInputValue -= out.value;
                // If we had enough token funds for our output we are a valid token output
                if (accumulatedTokenInputValue >= 0)
                    out.isToken = true;
                else
                    log.error("");
            }
        }
    }

    public Set<TxOutput> getAllUTXOs(Tx tx) {
        Set<TxOutput> allUTXOs = new HashSet<>();
        tx.outputs.stream()
                .filter(e -> e.isToken)
                .forEach(output -> {
                    if (!output.isSpent) {
                        allUTXOs.add(output);
                    } else {
                        allUTXOs.addAll(getAllUTXOs(output.inputOfSpendingTx.tx));
                    }
                });

        return allUTXOs;
    }

    public boolean isValidOutput(TxOutput output) {
        return !output.isSpent && output.isToken;
    }


    public boolean isValidInput(TxInput input) {
        return input.isToken || input.tx.id.equals(genesisTxId) || (input.output != null && input.output.isToken);
    }


}

