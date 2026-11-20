package bisq.core.offer;

import bisq.core.locale.FiatCurrency;
import bisq.core.monetary.Volume;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import org.bitcoinj.core.Coin;

import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferUtilTest {

    @Test
    public void testGetOfferIdWithMutationCounter() {
        assertEquals(
                OfferUtil.getOfferIdWithMutationCounter("R2WLX85L-0a7709f1-7857-4136-9a96-2ffdf377abe6-199"),
                "R2WLX85L-0a7709f1-7857-4136-9a96-2ffdf377abe6-199-1"
        );

        assertEquals(
                OfferUtil.getOfferIdWithMutationCounter("R2WLX85L-0a7709f1-7857-4136-9a96-2ffdf377abe6-199-4"),
                "R2WLX85L-0a7709f1-7857-4136-9a96-2ffdf377abe6-199-5"
        );
    }

    @Test
    public void testGetTxFeeByVsizeAveragesWithDepositTxVsize() {
        OfferUtil offerUtil = createOfferUtil();

        assertEquals(Coin.valueOf(424), offerUtil.getTxFeeByVsize(Coin.valueOf(2), 192));
    }

    @Test
    public void testGetTxFeeByVsizeRejectsInvalidVsize() {
        OfferUtil offerUtil = createOfferUtil();

        assertThrows(IllegalArgumentException.class, () -> offerUtil.getTxFeeByVsize(Coin.valueOf(2), 0));
        assertThrows(IllegalArgumentException.class, () -> offerUtil.getTxFeeByVsize(Coin.valueOf(2), -1));
    }

    @Test
    public void testIsFiatOffer() {
        Offer offer;

        offer = mock(Offer.class);
        when(offer.getBaseCurrencyCode()).thenReturn("BTC");
        when(offer.isBsqSwapOffer()).thenReturn(false);
        assertTrue(OfferUtil.isFiatOffer(offer));

        offer = mock(Offer.class);
        when(offer.getBaseCurrencyCode()).thenReturn("EUR");
        when(offer.isBsqSwapOffer()).thenReturn(false);
        assertFalse(OfferUtil.isFiatOffer(offer));

        offer = mock(Offer.class);
        when(offer.getBaseCurrencyCode()).thenReturn("BTC");
        when(offer.isBsqSwapOffer()).thenReturn(true);
        assertFalse(OfferUtil.isFiatOffer(offer));

        offer = mock(Offer.class);
        when(offer.getBaseCurrencyCode()).thenReturn("EUR");
        when(offer.isBsqSwapOffer()).thenReturn(false);
        assertFalse(OfferUtil.isFiatOffer(offer));
    }

    @Test
    public void testIsAltcoinOffer() {
        Offer offer;

        offer = mock(Offer.class);
        when(offer.getCounterCurrencyCode()).thenReturn("BTC");
        when(offer.isBsqSwapOffer()).thenReturn(false);
        assertTrue(OfferUtil.isAltcoinOffer(offer));

        offer = mock(Offer.class);
        when(offer.getCounterCurrencyCode()).thenReturn("EUR");
        when(offer.isBsqSwapOffer()).thenReturn(false);
        assertFalse(OfferUtil.isAltcoinOffer(offer));

        offer = mock(Offer.class);
        when(offer.getCounterCurrencyCode()).thenReturn("BTC");
        when(offer.isBsqSwapOffer()).thenReturn(true);
        assertFalse(OfferUtil.isAltcoinOffer(offer));

        offer = mock(Offer.class);
        when(offer.getCounterCurrencyCode()).thenReturn("EUR");
        when(offer.isBsqSwapOffer()).thenReturn(false);
        assertFalse(OfferUtil.isAltcoinOffer(offer));
    }

    // Positive control: 100 BSQ at 0.00002 BTC per BSQ and 100000 USD per BTC
    @Test
    public void testBsqFeeInUserFiatCurrency() {
        OfferUtil offerUtil = createOfferUtil(bsqStatistics(2000));

        Optional<Volume> fee = offerUtil.getFeeInUserFiatCurrency(Coin.valueOf(10_000), false, new BsqFormatter());

        assertEquals(Volume.parse("200", "USD").getValue(), fee.orElseThrow().getValue());
    }

    // A valid statistic can set the average BSQ price to 1 BSQ for 2 * 10^8 BTC. A fee of 100 BSQ in USD then
    // exceeds the long range. The fee is shown without its fiat value instead of failing the offer views.
    @Test
    public void testBsqFeeInUserFiatCurrencyIsEmptyIfItExceedsTheLongRange() {
        TradeStatistics3 bsqStatistics = bsqStatistics(20_000_000_000_000_000L);
        assertTrue(bsqStatistics.isValid());
        OfferUtil offerUtil = createOfferUtil(bsqStatistics);

        Optional<Volume> fee = offerUtil.getFeeInUserFiatCurrency(Coin.valueOf(10_000), false, new BsqFormatter());

        assertEquals(Optional.empty(), fee);
    }

    private static OfferUtil createOfferUtil() {
        return new OfferUtil(null, null, null, null, null, null, null, null);
    }

    private static OfferUtil createOfferUtil(TradeStatistics3 bsqStatistics) {
        Preferences preferences = mock(Preferences.class);
        when(preferences.getPreferredTradeCurrency()).thenReturn(new FiatCurrency("USD"));
        when(preferences.getBsqAverageTrimThreshold()).thenReturn(0.05);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        when(priceFeedService.getMarketPrice("USD")).thenReturn(new MarketPrice("USD", 100_000, 0, true));
        TradeStatisticsManager tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getNavigableTradeStatisticsSet()).thenReturn(new TreeSet<>(Set.of(bsqStatistics)));
        return new OfferUtil(null, null, null, preferences, priceFeedService, null, null, tradeStatisticsManager);
    }

    private static TradeStatistics3 bsqStatistics(long price) {
        return new TradeStatistics3("BSQ", price, Coin.parseCoin("2").value, PaymentMethod.BLOCK_CHAINS_ID,
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), null, null, (TreeMap<String, String>) null);
    }
}
