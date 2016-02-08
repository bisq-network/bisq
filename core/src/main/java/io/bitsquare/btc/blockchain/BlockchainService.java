package io.bitsquare.btc.blockchain;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bitsquare.btc.blockchain.providers.BlockTrailProvider;
import io.bitsquare.btc.blockchain.providers.BlockchainApiProvider;
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

    private final ArrayList<BlockchainApiProvider> providers;

    @Inject
    public BlockchainService() {
        providers = new ArrayList<>(Arrays.asList(new BlockrIOProvider(), new BlockTrailProvider(), new TradeBlockProvider()));
    }

    public SettableFuture<Coin> requestFeeFromBlockchain(String transactionId) {
        log.debug("Request fee from providers");
        long startTime = System.currentTimeMillis();
        final SettableFuture<Coin> resultFuture = SettableFuture.create();
        for (BlockchainApiProvider provider : providers) {
            GetFeeRequest getFeeRequest = new GetFeeRequest();
            SettableFuture<Coin> future = getFeeRequest.requestFee(transactionId, provider);
            Futures.addCallback(future, new FutureCallback<Coin>() {
                public void onSuccess(Coin fee) {
                    if (!resultFuture.isDone()) {
                        log.info("Request fee from providers done after {} ms.", (System.currentTimeMillis() - startTime));
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
