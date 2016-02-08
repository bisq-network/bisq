package io.bitsquare.btc.http;

import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.Serializable;

public interface BlockchainApiProvider extends Serializable {
    Coin getFee(String transactionId) throws IOException, HttpException;
}
