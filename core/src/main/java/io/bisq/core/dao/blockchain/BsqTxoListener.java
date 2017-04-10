package io.bisq.core.dao.blockchain;


public interface BsqTxoListener {
    void onBsqTxoChanged(BsqTXOMap bsqTXOMap);
}
