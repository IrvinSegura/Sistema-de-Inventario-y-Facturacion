package com.irvin.inventory.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a controller method limited per client address; RateLimitInterceptor answers 429 when the
// bucket is empty, before the request body is read.
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitPolicy value();
}
