package io.bisq.core.dao.compensation;

import org.bitcoinj.core.Coin;

public class CompensationAmountException extends Exception {
    public Coin neededAmount;
    public Coin askedAmount;
    public CompensationAmountException(Coin neededAmount, Coin askedAmount){
        this.neededAmount = neededAmount;
        this.askedAmount = askedAmount;
    }
}
