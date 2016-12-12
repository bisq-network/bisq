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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SquUTXOProvider implements UTXOProvider {
    private static final Logger log = LoggerFactory.getLogger(SquUTXOProvider.class);

    private Map<String, UTXO> utxoByAddressAsStringMap = new HashMap<>();
    private NetworkParameters params;

    @Inject
    public SquUTXOProvider(Preferences preferences) {
        params = preferences.getBitcoinNetwork().getParameters();
    }

    public void setUtxoSet(Set<UTXO> utxoSet) {
        utxoSet.stream().forEach(e -> utxoByAddressAsStringMap.put(e.getAddress(), e));
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        return addresses.stream().map(e -> utxoByAddressAsStringMap.get(e.toString())).filter(e -> e != null).collect(Collectors.toList());
    }

    @Override
    public int getChainHeadHeight() throws UTXOProviderException {
        //TODO
        return 331;
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }
}
