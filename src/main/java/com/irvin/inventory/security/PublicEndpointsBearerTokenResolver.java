package com.irvin.inventory.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

// The public auth endpoints ignore any bearer token: clients may still send one their user's password
// or role change has just made invalid (CredentialsVersionValidator), and it must not turn signing in
// again or out into a 401.
public class PublicEndpointsBearerTokenResolver implements BearerTokenResolver {

    private static final String PUBLIC_PREFIX = "/api/v1/auth/";

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + PUBLIC_PREFIX)
                ? null
                : delegate.resolve(request);
    }
}
