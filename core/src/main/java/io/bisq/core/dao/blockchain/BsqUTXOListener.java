package io.bisq.core.dao.blockchain;


public interface BsqUTXOListener {
    void onBsqUTXOChanged(BsqUTXOMap bsqUTXOMap);
}
