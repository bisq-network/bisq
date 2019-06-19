package bisq.api.http.model.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



import javax.validation.Constraint;
import javax.validation.Payload;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CountryCodeValidator.class)
@Documented
public @interface CountryCode {

    String message() default "is not valid country code";

    @SuppressWarnings("unused")
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
