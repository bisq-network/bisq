package bisq.core.offer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferUtilTest {
    @Test
    public void testGetRandomOfferId() {
        String offerId = OfferUtil.getRandomOfferId();
        assertTrue(offerId.matches("\\w{5,8}-\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12}-\\w{3}"));
    }

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
}
