package com.irvin.inventory.catalog;

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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

// VAT rate as a fraction (0.16 = 16%), matching NUMERIC(5,4) and the DB CHECK.
@Documented
@DecimalMin("0.0")
@DecimalMax(value = "1.0", inclusive = false)
@Digits(integer = 1, fraction = 4)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface TaxRate {

    String message() default "must be a fraction between 0 and 1 with up to 4 decimals (e.g. 0.16)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
