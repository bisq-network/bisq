package io.bitsquare.btc.blockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    // Once needed will be moved to Providers so we stick with out hidden service routing
    
    /*private final ArrayList<BlockchainTxProvider> blockchainTxProviders;

    @Inject
    public BlockchainService(BlockrIOProvider blockrIOProvider, BlockTrailProvider blockTrailProvider, TradeBlockProvider tradeBlockProvider) {
        blockchainTxProviders = new ArrayList<>(Arrays.asList(blockrIOProvider, blockTrailProvider, tradeBlockProvider));
    }

    public SettableFuture<Coin> requestFee(String transactionId) {
        Log.traceCall(transactionId);
        long startTime = System.currentTimeMillis();
        final SettableFuture<Coin> resultFuture = SettableFuture.create();

        for (BlockchainTxProvider provider : blockchainTxProviders) {
            GetTransactionRequest getTransactionRequest = new GetTransactionRequest();
            SettableFuture<Coin> future = getTransactionRequest.request(transactionId, provider);
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
    }*/
}
