package io.bisq.core.btc.wallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

public class InsufficientBsqException extends InsufficientMoneyException {
    public InsufficientBsqException(Coin missing) {
        super(missing);
    }
}
