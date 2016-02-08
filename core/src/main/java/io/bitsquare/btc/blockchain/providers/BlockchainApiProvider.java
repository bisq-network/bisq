package io.bitsquare.btc.blockchain.providers;

import io.bitsquare.btc.blockchain.HttpException;
import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.Serializable;

public interface BlockchainApiProvider extends Serializable {
    Coin getFee(String transactionId) throws IOException, HttpException;
}
