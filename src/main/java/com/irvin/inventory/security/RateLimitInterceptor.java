package com.irvin.inventory.security;

import com.irvin.inventory.common.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// Applies @RateLimited before the body is read, so invalid requests count too. The client address
// is the connection's, or the one the proxy reported when server.forward-headers-strategy trusts it
// (see application-prod.yaml); never a header read here.
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ClientRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RateLimited rateLimited = method.getMethodAnnotation(RateLimited.class);
            if (rateLimited != null) {
                rateLimiter.tryAcquire(rateLimited.value(), request.getRemoteAddr()).ifPresent(wait -> {
                    throw new TooManyRequestsException(wait);
                });
            }
        }
        return true;
    }
}
