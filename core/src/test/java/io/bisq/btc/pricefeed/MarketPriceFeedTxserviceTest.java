package io.bisq.btc.pricefeed;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class MarketPriceFeedTxserviceTest {
    private static final Logger log = LoggerFactory.getLogger(MarketPriceFeedTxserviceTest.class);

   /* @Test
    public void testGetPrice() throws InterruptedException {
        PriceFeedService priceFeedService = new PriceFeedService(null, null, true);
        priceFeedService.setCurrencyCode("EUR");
        priceFeedService.init(tradeCurrency -> {
                    log.debug(tradeCurrency.toString());
                    assertTrue(true);
                },
                (errorMessage, throwable) -> {
                    log.debug(errorMessage);
                    assertTrue(false);
                }
        );
        Thread.sleep(10000);
    }*/
}
