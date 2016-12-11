package io.bitsquare.btc.provider.squ;

import org.bitcoinj.core.UTXO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class SquUtxoFeedData {
    private static final Logger log = LoggerFactory.getLogger(SquUtxoFeedData.class);
    private Set<UTXO> utxoSet;

    public SquUtxoFeedData(Set<UTXO> utxoSet) {
        this.utxoSet = utxoSet;
    }

    public Set<UTXO> getUtxoSet() {
        return utxoSet;
    }
}
