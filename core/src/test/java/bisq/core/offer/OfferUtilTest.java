package bisq.core.offer;


import org.bitcoinj.core.Coin;

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

    private static OfferUtil createOfferUtil() {
        return new OfferUtil(null, null, null, null, null, null, null, null);
    }
}
