package com.irvin.inventory.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // E.164; optional, needed to receive password reset codes by SMS.
    @Column(length = 16)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    // Set when an admin resets the password to a temporary one; the user can't do anything else until
    // they change it (PasswordChangeInterceptor), which clears it, as does a forgotten-password reset.
    @Column(nullable = false)
    private boolean passwordChangeRequired;

    // Goes up when the password, role or status changes; access tokens carry the version they were issued
    // with, and CredentialsVersionValidator refuses older ones.
    @Column(nullable = false)
    private int credentialsVersion;

    // Access tokens issued before stop working (CredentialsVersionValidator). Refresh tokens are revoked
    // separately (AuthService.revokeAllSessions).
    public void invalidateIssuedTokens() {
        credentialsVersion++;
    }

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
