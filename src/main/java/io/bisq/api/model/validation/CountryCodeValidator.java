package io.bisq.api.model.validation;

import io.bisq.common.locale.CountryUtil;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class CountryCodeValidator implements ConstraintValidator<CountryCode, String> {

    @Override
    public void initialize(CountryCode constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return CountryUtil.findCountryByCode(value).isPresent();
    }
}
