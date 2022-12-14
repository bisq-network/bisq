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

package bisq.core.dao.burningman.accounting.blockchain.temp;

import bisq.core.dao.node.full.rpc.dto.DtoPubKeyScript;
import bisq.core.dao.node.full.rpc.dto.RawDtoTransaction;
import bisq.core.dao.state.model.blockchain.ScriptType;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
@Getter
public final class TempAccountingTx {
    private final String txId;
    private final boolean isValidDptLockTime;
    private final List<TempAccountingTxInput> inputs;
    private final List<TempAccountingTxOutput> outputs;

    public TempAccountingTx(RawDtoTransaction tx) {
        txId = tx.getTxId();

        // If lockTime is < 500000000 it is interpreted as block height, otherwise as unix time. We use block height.
        // We only handle blocks from EARLIEST_BLOCK_HEIGHT on
        //todo for dev testing
        isValidDptLockTime = /*tx.getLockTime() >= BurningManAccountingService.EARLIEST_BLOCK_HEIGHT &&*/ tx.getLockTime() < 500000000;

        inputs = tx.getVIn().stream()
                .map(input -> {
                    List<String> txInWitness = input.getTxInWitness() != null ? input.getTxInWitness() : new ArrayList<>();
                    return new TempAccountingTxInput(input.getSequence(), txInWitness);
                })
                .collect(Collectors.toList());

        outputs = tx.getVOut().stream()
                .map(output -> {
                    long value = BigDecimal.valueOf(output.getValue()).movePointRight(8).longValueExact();
                    // We use a non-null field for address as in the final object we require that the address is available
                    String address = "";
                    DtoPubKeyScript scriptPubKey = output.getScriptPubKey();
                    if (scriptPubKey != null) {
                        List<String> addresses = scriptPubKey.getAddresses();
                        if (addresses != null && addresses.size() == 1) {
                            address = addresses.get(0);
                        }
                    }
                    ScriptType scriptType = output.getScriptPubKey() != null ? output.getScriptPubKey().getType() : null;
                    return new TempAccountingTxOutput(value, address, scriptType);
                })
                .collect(Collectors.toList());
    }
}
