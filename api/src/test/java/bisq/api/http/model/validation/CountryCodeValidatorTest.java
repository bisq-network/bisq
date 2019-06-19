package bisq.api.http.model.validation;

import bisq.core.locale.CountryUtil;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CountryCodeValidatorTest {

    @Test
    public void isValid_nullValue_returnTrue() {
        //        Given
        CountryCodeValidator validator = new CountryCodeValidator();

        //        When
        boolean result = validator.isValid(null, null);

        //        Then
        assertTrue(result);
    }

    @Test
    public void isValid_unsupportedCountryCode_returnFalse() {
        //        Given
        CountryCodeValidator validator = new CountryCodeValidator();
        String countryCode = "USA";
        assertFalse(CountryUtil.findCountryByCode(countryCode).isPresent());

        //        When
        boolean result = validator.isValid(countryCode, null);

        //        Then
        assertFalse(result);
    }

    @Test
    public void isValid_supportedCountryCode_returnTrue() {
        //        Given
        CountryCodeValidator validator = new CountryCodeValidator();
        String currencyCode = "US";
        assertTrue(CountryUtil.findCountryByCode(currencyCode).isPresent());

        //        When
        boolean result = validator.isValid(currencyCode, null);

        //        Then
        assertTrue(result);
    }
}
