package com.darshan.lending.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MinAgeValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface MinAge {

    int value() default 18;

    String message() default "User must be at least 18 years old to register";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
