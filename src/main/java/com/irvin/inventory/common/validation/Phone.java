package com.irvin.inventory.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

// At least 7 digits, with optional +, spaces, dashes and parentheses; fits VARCHAR(20).
@Documented
@Pattern(regexp = "^(?=(?:\\D*\\d){7})[0-9+()\\- ]{7,20}$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface Phone {

    String message() default "must have at least 7 digits and only digits, spaces, +, - or parentheses";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
