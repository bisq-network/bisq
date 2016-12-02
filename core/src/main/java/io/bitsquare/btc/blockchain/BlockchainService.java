package io.bitsquare.btc.blockchain;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.btc.blockchain.providers.BlockTrailProvider;
import io.bitsquare.btc.blockchain.providers.BlockchainTxProvider;
import io.bitsquare.btc.blockchain.providers.BlockrIOProvider;
import io.bitsquare.btc.blockchain.providers.TradeBlockProvider;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;

public class BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private final ArrayList<BlockchainTxProvider> blockchainTxProviders;

    @Inject
    public BlockchainService(BlockrIOProvider blockrIOProvider, BlockTrailProvider blockTrailProvider, TradeBlockProvider tradeBlockProvider) {
        blockchainTxProviders = new ArrayList<>(Arrays.asList(blockrIOProvider, blockTrailProvider, tradeBlockProvider));
    }

    public SettableFuture<Coin> requestFee(String transactionId) {
        Log.traceCall(transactionId);
        long startTime = System.currentTimeMillis();
        final SettableFuture<Coin> resultFuture = SettableFuture.create();

        for (BlockchainTxProvider provider : blockchainTxProviders) {
            GetFeeRequest getFeeRequest = new GetFeeRequest();
            SettableFuture<Coin> future = getFeeRequest.request(transactionId, provider);
            Futures.addCallback(future, new FutureCallback<Coin>() {
                public void onSuccess(Coin fee) {
                    if (!resultFuture.isDone()) {
                        log.debug("Request fee from providers done after {} ms.", (System.currentTimeMillis() - startTime));
                        resultFuture.set(fee);
                    }
                }

                public void onFailure(@NotNull Throwable throwable) {
                    if (!resultFuture.isDone()) {
                        log.warn("Could not get the fee from any provider after repeated requests.");
                        resultFuture.setException(throwable);
                    }
                }
            });
        }
        return resultFuture;
    }
}
