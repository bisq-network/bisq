package bisq.desktop.util.validation;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.util.validation.RegexValidator;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapitualValidatorTest {
    @Before
    public void setup() {
        final BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        final String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);
    }

    @Test
    public void validate() {
        CapitualValidator validator = new CapitualValidator(
                new RegexValidator()
        );

        assertTrue(validator.validate("CAP-123456").isValid);
        assertTrue(validator.validate("CAP-XXXXXX").isValid);
        assertTrue(validator.validate("CAP-123XXX").isValid);

        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate(null).isValid);
        assertFalse(validator.validate("123456").isValid);
        assertFalse(validator.validate("XXXXXX").isValid);
        assertFalse(validator.validate("123XXX").isValid);
        assertFalse(validator.validate("12XXX").isValid);
        assertFalse(validator.validate("CAP-12XXX").isValid);
        assertFalse(validator.validate("CA-12XXXx").isValid);
    }
}
