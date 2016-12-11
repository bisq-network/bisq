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

package io.bitsquare.btc.wallet;

import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class SquUTXOProvider implements UTXOProvider {
    private static final Logger log = LoggerFactory.getLogger(SquUTXOProvider.class);

    private Map<String, UTXO> utxoByAddressAsStringMap = new HashMap<>();
    private Set<String> addressesAsStringSet = new HashSet<>();
    private Set<UTXO> transactionOutputSet = new HashSet<>();
    private NetworkParameters params;

    @Inject
    public SquUTXOProvider(Preferences preferences) {
        params = preferences.getBitcoinNetwork().getParameters();

        setTransactions();
    }

    private void setTransactions() {
        Transaction tx = new Transaction(null);
        boolean isCoinBase = tx.isCoinBase();
        int height = 1;
        Coin valueOut = Coin.ZERO;
        Sha256Hash hash = tx.getHash();
        for (TransactionOutput out : tx.getOutputs()) {
            valueOut = valueOut.add(out.getValue());
            // For each output, add it to the set of unspent outputs so it can be consumed in future.
            Script script = getScript(out.getScriptBytes());
            UTXO newOut = new UTXO(hash,
                    out.getIndex(),
                    out.getValue(),
                    height,
                    isCoinBase,
                    script,
                    getScriptAddress(script));
            utxoByAddressAsStringMap.put(newOut.getAddress(), newOut);
        }
    }


    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        // addressesAsStringSet.clear();
        // addressesAsStringSet.addAll(addresses.stream().map(Address::toString).collect(Collectors.toSet()));
        List<UTXO> foundOutputs = addresses.stream().map(e -> utxoByAddressAsStringMap.get(e.toString())).filter(e -> e != null).collect(Collectors.toList());

        // List<UTXO> foundOutputs = transactionOutputSet.stream().filter(e -> addressesAsStringSet.contains(e.getAddress())).collect(Collectors.toList());
     /*   
        List<UTXO> foundOutputs = new ArrayList<UTXO>();
        Collection<UTXO> outputsList = transactionOutputMap.values();
        for (UTXO output : outputsList) {
            for (Address address : addresses) {
                if (output.getAddress().equals(address.toString())) {
                    foundOutputs.add(output);
                }
            }
        }*/
        return foundOutputs;
    }

    @Override
    public int getChainHeadHeight() throws UTXOProviderException {
        return 0;
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }

    private String getScriptAddress(@Nullable Script script) {
        String address = "";
        try {
            if (script != null) {
                address = script.getToAddress(params, true).toString();
            }
        } catch (Exception e) {
        }
        return address;
    }

    private Script getScript(byte[] scriptBytes) {
        try {
            return new Script(scriptBytes);
        } catch (Exception e) {
            return new Script(new byte[0]);
        }
    }
}
