package io.bitsquare.btc.blockchain.providers;

import io.bitsquare.http.HttpException;
import org.bitcoinj.core.Coin;

import java.io.IOException;

public interface FeeProvider {
    Coin getFee(String transactionId) throws IOException, HttpException;
}
