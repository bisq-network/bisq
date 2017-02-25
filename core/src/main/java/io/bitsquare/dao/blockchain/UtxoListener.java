package io.bitsquare.dao.blockchain;


import java.util.Map;

public interface UtxoListener {
    void onUtxoChanged(Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap);
}
