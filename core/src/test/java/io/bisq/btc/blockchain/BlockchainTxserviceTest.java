package io.bisq.btc.blockchain;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class BlockchainTxserviceTest {
    private static final Logger log = LoggerFactory.getLogger(BlockchainTxserviceTest.class);

   /* @Test
    public void testGetFee() throws InterruptedException {
        BlockchainService blockchainService = new BlockchainService(null, null, null);

        // that tx has 0.001 BTC as fee
        String transactionId = "38d176d0b1079b99fcb59859401d6b1679d2fa18fd8989d2c244b3682e52fce6";

        SettableFuture<Coin> future = blockchainService.requestFee(transactionId);
        Futures.addCallback(future, new FutureCallback<Coin>() {
            public void onSuccess(Coin fee) {
                log.debug(fee.toFriendlyString());
                assertTrue(fee.equals(Coin.MILLICOIN));
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.error(throwable.getMessage());
            }
        });
        Thread.sleep(5000);
    }*/
}
