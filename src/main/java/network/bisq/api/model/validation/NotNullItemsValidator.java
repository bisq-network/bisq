package network.bisq.api.model.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Collection;

public class NotNullItemsValidator implements ConstraintValidator<NotNullItems, Collection> {

    @Override
    public void initialize(NotNullItems constraintAnnotation) {
    }

    @Override
    public boolean isValid(Collection value, ConstraintValidatorContext context) {
        return null == value || !value.contains(null);
    }
}
