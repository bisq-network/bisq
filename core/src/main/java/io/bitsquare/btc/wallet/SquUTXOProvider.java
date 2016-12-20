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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

public class SquUTXOProvider implements UTXOProvider {
    private static final Logger log = LoggerFactory.getLogger(SquUTXOProvider.class);

    private Map<String, Set<UTXO>> utxoSetByAddressMap = new HashMap<>();
    private NetworkParameters parameters;

    @Inject
    public SquUTXOProvider(Preferences preferences) {
        this.parameters = preferences.getBitcoinNetwork().getParameters();
    }

    public void setUtxoSet(Set<UTXO> utxoSet) {
        utxoSet.stream().forEach(utxo -> {
            String address = utxo.getAddress();
            if (!utxoSetByAddressMap.containsKey(address))
                utxoSetByAddressMap.put(address, new HashSet<>());

            utxoSetByAddressMap.get(address).add(utxo);
        });
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        List<UTXO> result = new ArrayList<>();
        addresses.stream()
                .filter(address -> utxoSetByAddressMap.containsKey(address.toString()))
                .forEach(address -> result.addAll(utxoSetByAddressMap.get(address.toString())));
        return result;
    }

    @Override
    public int getChainHeadHeight() throws UTXOProviderException {
        return 0;
    }

    @Override
    public NetworkParameters getParams() {
        return parameters;
    }
}
