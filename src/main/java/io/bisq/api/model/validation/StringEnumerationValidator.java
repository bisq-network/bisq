package io.bisq.api.model.validation;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.TreeSet;

public class StringEnumerationValidator implements ConstraintValidator<StringEnumeration, String> {

    private Set<String> availableValues;

    @Override
    public void initialize(StringEnumeration constraintAnnotation) {
        availableValues = new TreeSet<>();
        final Enum<?>[] enums = constraintAnnotation.enumClass().getEnumConstants();
        for (Enum<?> anEnum : enums)
            availableValues.add(anEnum.name());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        final boolean valid = value == null || availableValues.contains(value);
        if (!valid) {
            final HibernateConstraintValidatorContext hibernateContext = context.unwrap(HibernateConstraintValidatorContext.class);
            hibernateContext.addExpressionVariable("availableValues", getAvailableValuesAsString());
        }
        return valid;
    }

    private String getAvailableValuesAsString() {
        final StringBuilder builder = new StringBuilder();
        for (String item : availableValues) {
            if (0 < builder.length()) builder.append(", ");
            builder.append(item);
        }
        return builder.toString();
    }
}
