package io.bitsquare.btc.provider.squ;

import org.bitcoinj.core.UTXO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class BsqUtxoFeedData {
    private static final Logger log = LoggerFactory.getLogger(BsqUtxoFeedData.class);
    private Set<UTXO> utxoSet;

    public BsqUtxoFeedData(Set<UTXO> utxoSet) {
        this.utxoSet = utxoSet;
    }

    public Set<UTXO> getUtxoSet() {
        return utxoSet;
    }
}
