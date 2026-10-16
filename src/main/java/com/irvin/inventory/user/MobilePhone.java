package com.irvin.inventory.user;

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

// E.164, the format SMS providers need: + country code + number, 8 to 15 digits (+525512345678).
// Apply after Normalization.phone, which removes spaces, dashes, dots and parentheses.
@Documented
@Pattern(regexp = "^\\+[1-9]\\d{7,14}$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface MobilePhone {

    String message() default "must be in international format, e.g. +525512345678";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
