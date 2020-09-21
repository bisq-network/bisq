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

public class AdvancedCashValidatorTest {
    @Before
    public void setup() {
        final BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        final String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);
    }

    @Test
    public void validate(){
        AdvancedCashValidator validator = new AdvancedCashValidator(
                new EmailValidator(),
                new RegexValidator()
        );

        assertTrue(validator.validate("U123456789012").isValid);
        assertTrue(validator.validate("test@user.com").isValid);

        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate(null).isValid);
        assertFalse(validator.validate("123456789012").isValid);
    }
}
