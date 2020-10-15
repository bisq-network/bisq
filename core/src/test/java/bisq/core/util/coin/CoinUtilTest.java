package bisq.core.util.coin;

import bisq.core.monetary.Price;

import org.bitcoinj.core.Coin;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class CoinUtilTest {

    @Test
    public void testGetFeePerBtc() {
        assertEquals(Coin.parseCoin("1"), CoinUtil.getFeePerBtc(Coin.parseCoin("1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.1"), CoinUtil.getFeePerBtc(Coin.parseCoin("0.1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.01"), CoinUtil.getFeePerBtc(Coin.parseCoin("0.1"), Coin.parseCoin("0.1")));
        assertEquals(Coin.parseCoin("0.015"), CoinUtil.getFeePerBtc(Coin.parseCoin("0.3"), Coin.parseCoin("0.05")));
    }

    @Test
    public void testMinCoin() {
        assertEquals(Coin.parseCoin("1"), CoinUtil.minCoin(Coin.parseCoin("1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.1"), CoinUtil.minCoin(Coin.parseCoin("0.1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.01"), CoinUtil.minCoin(Coin.parseCoin("0.1"), Coin.parseCoin("0.01")));
        assertEquals(Coin.parseCoin("0"), CoinUtil.minCoin(Coin.parseCoin("0"), Coin.parseCoin("0.05")));
        assertEquals(Coin.parseCoin("0"), CoinUtil.minCoin(Coin.parseCoin("0.05"), Coin.parseCoin("0")));
    }

    @Test
    public void testMaxCoin() {
        assertEquals(Coin.parseCoin("1"), CoinUtil.maxCoin(Coin.parseCoin("1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("1"), CoinUtil.maxCoin(Coin.parseCoin("0.1"), Coin.parseCoin("1")));
        assertEquals(Coin.parseCoin("0.1"), CoinUtil.maxCoin(Coin.parseCoin("0.1"), Coin.parseCoin("0.01")));
        assertEquals(Coin.parseCoin("0.05"), CoinUtil.maxCoin(Coin.parseCoin("0"), Coin.parseCoin("0.05")));
        assertEquals(Coin.parseCoin("0.05"), CoinUtil.maxCoin(Coin.parseCoin("0.05"), Coin.parseCoin("0")));
    }

    @Test
    public void testGetAdjustedAmount() {
        Coin result = CoinUtil.getAdjustedAmount(
                Coin.valueOf(100_000),
                Price.valueOf("USD", 1000_0000),
                20_000_000,
                1);
        assertEquals(
                "Minimum trade amount allowed should be adjusted to the smallest trade allowed.",
                "0.001 BTC",
                result.toFriendlyString()
        );

        try {
            CoinUtil.getAdjustedAmount(
                    Coin.ZERO,
                    Price.valueOf("USD", 1000_0000),
                    20_000_000,
                    1);
            fail("Expected IllegalArgumentException to be thrown when amount is too low.");
        } catch (IllegalArgumentException iae) {
            assertEquals(
                    "Unexpected exception message.",
                    "amount needs to be above minimum of 10k satoshis",
                    iae.getMessage()
            );
        }

        result = CoinUtil.getAdjustedAmount(
                Coin.valueOf(1_000_000),
                Price.valueOf("USD", 1000_0000),
                20_000_000,
                1);
        assertEquals(
                "Minimum allowed trade amount should not be adjusted.",
                "0.01 BTC",
                result.toFriendlyString()
        );

        result = CoinUtil.getAdjustedAmount(
                Coin.valueOf(100_000),
                Price.valueOf("USD", 1000_0000),
                1_000_000,
                1);
        assertEquals(
                "Minimum trade amount allowed should respect maxTradeLimit and factor, if possible.",
                "0.001 BTC",
                result.toFriendlyString()
        );

        // TODO(chirhonul): The following seems like it should raise an exception or otherwise fail.
        // We are asking for the smallest allowed BTC trade when price is 1000 USD each, and the
        // max trade limit is 5k sat = 0.00005 BTC. But the returned amount 0.00005 BTC, or
        // 0.05 USD worth, which is below the factor of 1 USD, but does respect the maxTradeLimit.
        // Basically the given constraints (maxTradeLimit vs factor) are impossible to both fulfill..
        result = CoinUtil.getAdjustedAmount(
                Coin.valueOf(100_000),
                Price.valueOf("USD", 1000_0000),
                5_000,
                1);
        assertEquals(
                "Minimum trade amount allowed with low maxTradeLimit should still respect that limit, even if result does not respect the factor specified.",
                "0.00005 BTC",
                result.toFriendlyString()
        );
    }
}
