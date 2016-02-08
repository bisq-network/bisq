package io.bitsquare.btc.blockchain;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.TestCase.assertTrue;

public class BlockchainServiceTest {
    private static final Logger log = LoggerFactory.getLogger(BlockchainServiceTest.class);

    @Test
    public void testIsMinSpendableAmount() throws InterruptedException {
        BlockchainService blockchainService = new BlockchainService();

        // that tx has 0.001 BTC as fee
        String transactionId = "38d176d0b1079b99fcb59859401d6b1679d2fa18fd8989d2c244b3682e52fce6";

        SettableFuture<Coin> future = blockchainService.requestFeeFromBlockchain(transactionId);
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
    }
}
