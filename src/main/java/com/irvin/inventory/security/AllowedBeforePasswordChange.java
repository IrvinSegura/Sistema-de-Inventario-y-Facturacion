package com.irvin.inventory.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Endpoints a user who must change a temporary password can still call (PasswordChangeInterceptor):
// reading their profile, changing the password, and the public auth ones (sign in, refresh, sign out).
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedBeforePasswordChange {
}
