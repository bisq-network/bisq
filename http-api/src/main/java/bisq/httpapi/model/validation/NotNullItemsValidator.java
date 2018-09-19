package bisq.httpapi.model.validation;

import java.util.Collection;



import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class NotNullItemsValidator implements ConstraintValidator<NotNullItems, Collection> {

    @Override
    public void initialize(NotNullItems constraintAnnotation) {
    }

    @Override
    public boolean isValid(Collection value, ConstraintValidatorContext context) {
        return null == value || !value.contains(null);
    }
}
