package com.irvin.inventory.security;

import com.irvin.inventory.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// Temporary password (token claim pwd_change): only @AllowedBeforePasswordChange endpoints answer, the
// rest get 403, so the admin who typed it can't use the account. Why a claim: docs/data-model.md.
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method
                && !method.hasMethodAnnotation(AllowedBeforePasswordChange.class)
                && !method.getBeanType().isAnnotationPresent(AllowedBeforePasswordChange.class)
                && mustChangePassword(SecurityContextHolder.getContext().getAuthentication())) {
            throw new ForbiddenException("Password change required");
        }
        return true;
    }

    private static boolean mustChangePassword(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(TokenService.PASSWORD_CHANGE_CLAIM));
    }
}
