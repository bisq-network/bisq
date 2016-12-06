/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.tokens;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TransactionParser {
    private static final Logger log = LoggerFactory.getLogger(TransactionParser.class);

    private String genesisTxId = "83a4454747e5c972f2eb20d587538a330dd30b5cf468f8faea32eae640cebe79";
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


    public void start() {
        Tx genesisTx = getTx(genesisTxId);
        List<TxOutput> allUTXOs = findAllUTXOs(genesisTx);
    }

    public List<TxOutput> findAllUTXOs(Tx tx) {
        List<TxOutput> allUTXOs = new ArrayList<>();
        getOutputs(tx).stream().forEach(txOutput -> {
            if (txOutput.isSpent) {
                allUTXOs.addAll(findAllUTXOs(txOutput.spentByTxInput.parentTx));
            } else if (isValidOutput(txOutput)) {
                allUTXOs.add(txOutput);
            } else {
                log.warn("invalid output " + txOutput);
            }
        });

        return allUTXOs;
    }

    public boolean isValidOutput(TxOutput output) {
        output.parentTx.inputs.forEach(input -> {
            if (isValidInput(input)) {

            }
        });
        return true;
    }

    public boolean isValidInput(TxInput input) {
        input.parentTx.outputs.forEach(output -> {

        });
        return true;
    }

    private List<TxOutput> getOutputs(Tx tx) {
        return new ArrayList<>();
    }


}

