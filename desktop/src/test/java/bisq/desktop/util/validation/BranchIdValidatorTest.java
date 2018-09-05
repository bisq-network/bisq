package bisq.desktop.util.validation;

import bisq.core.locale.Res;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BranchIdValidatorTest {

    @Before
    public void setup() {
        Locale.setDefault(new Locale("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testValidationForArgentina() {
        BranchIdValidator validator = new BranchIdValidator("AR");

        assertTrue(validator.validate("0590").isValid);

        assertFalse(validator.validate("05901").isValid);
    }

}
