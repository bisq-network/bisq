package io.bisq.core.offer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(OfferPayload.class)
public class OfferTest {

    @Test
    public void testHasNoRange() {
        OfferPayload payload = mock(OfferPayload.class);
        when(payload.getMinAmount()).thenReturn(1000L);
        when(payload.getAmount()).thenReturn(1000L);

        Offer offer = new Offer(payload);
        assertFalse(offer.isRange());
    }

    @Test
    public void testHasRange() {
        OfferPayload payload = mock(OfferPayload.class);
        when(payload.getMinAmount()).thenReturn(1000L);
        when(payload.getAmount()).thenReturn(2000L);

        Offer offer = new Offer(payload);
        assertTrue(offer.isRange());
    }
}
