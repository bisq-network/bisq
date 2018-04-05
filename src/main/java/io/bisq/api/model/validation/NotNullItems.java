package io.bisq.api.model.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotNullItemsValidator.class)
@Documented
public @interface NotNullItems {

    String message() default "must not contain null elements";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
