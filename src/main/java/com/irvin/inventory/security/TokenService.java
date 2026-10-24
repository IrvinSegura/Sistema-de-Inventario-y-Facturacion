package com.irvin.inventory.security;

import java.time.Clock;
import java.time.Instant;

import com.irvin.inventory.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String ISSUER = "inventory-billing-system";
    public static final String ROLE_CLAIM = "role";
    // Present (true) only while the user must change a temporary password; see PasswordChangeInterceptor.
    public static final String PASSWORD_CHANGE_CLAIM = "pwd_change";
    // The user's credentials version when issued; see CredentialsVersionValidator.
    public static final String CREDENTIALS_VERSION_CLAIM = "cv";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public String createAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim(CREDENTIALS_VERSION_CLAIM, user.getCredentialsVersion())
                .claims(all -> {
                    if (user.isPasswordChangeRequired()) {
                        all.put(PASSWORD_CHANGE_CLAIM, true);
                    }
                })
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
