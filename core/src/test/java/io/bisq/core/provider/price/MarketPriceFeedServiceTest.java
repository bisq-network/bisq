package io.bisq.core.provider.price;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.TestCase.assertTrue;

@Ignore
public class MarketPriceFeedServiceTest {
    private static final Logger log = LoggerFactory.getLogger(MarketPriceFeedServiceTest.class);

    @Test
    public void testGetPrice() throws InterruptedException {
        PriceFeedService priceFeedService = new PriceFeedService(null, null, null);
        priceFeedService.setCurrencyCode("EUR");
        priceFeedService.requestPriceFeed(tradeCurrency -> {
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
