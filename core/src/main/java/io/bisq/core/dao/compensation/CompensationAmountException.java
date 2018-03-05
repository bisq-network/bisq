package io.bisq.core.dao.compensation;

import org.bitcoinj.core.Coin;

public class CompensationAmountException extends Exception {
    public Coin required;
    public Coin provided;

    public CompensationAmountException(Coin required, Coin provided) {
        this.required = required;
        this.provided = provided;
    }
}
