package io.bitsquare.btc.pricefeed;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.TestCase.assertTrue;

@Ignore
public class MarketPriceFeedTest {
    private static final Logger log = LoggerFactory.getLogger(MarketPriceFeedTest.class);

    @Test
    public void testGetPrice() throws InterruptedException {
        PriceFeed priceFeed = new PriceFeed();
        priceFeed.setCurrencyCode("EUR");
        priceFeed.init(tradeCurrency -> {
                    log.debug(tradeCurrency.toString());
                    assertTrue(true);
                },
                (errorMessage, throwable) -> {
                    log.debug(errorMessage);
                    assertTrue(false);
                }
        );
        Thread.sleep(10000);
    }
}
