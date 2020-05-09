package bisq.common.util;

import org.junit.Test;



import junit.framework.TestCase;

public class ReasonsForPaymentTests extends TestCase {
    @Test
    public void testWhenStringIdIsProvidedReasonIsReturned() {
        assertEquals("Thank you, friend", ReasonsForPayment.getReason(""));

        assertEquals("Grab a snack on me!", ReasonsForPayment.getReason("G"));
        assertEquals("Miss you", ReasonsForPayment.getReason("H"));
    }
}
