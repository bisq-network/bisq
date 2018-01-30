package io.bisq.common.locale;

import org.junit.Before;
import org.junit.Test;

import java.util.Locale;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CurrencyUtilTest {

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
    }

    @Test
    public void testGetTradeCurrency() {
        Optional<TradeCurrency> euro = CurrencyUtil.getTradeCurrency("EUR");
        Optional<TradeCurrency> naira = CurrencyUtil.getTradeCurrency("NGN");
        Optional<TradeCurrency> fake = CurrencyUtil.getTradeCurrency("FAK");


        assertTrue(euro.isPresent());
        assertTrue(naira.isPresent());
        assertFalse("Fake currency shouldn't exist",fake.isPresent());
    }
}
