package com.irvin.inventory.auth;

import java.time.Instant;

import com.irvin.inventory.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ResetChannel channel;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    // Used, replaced by a newer code or locked after too many wrong attempts.
    private Instant invalidatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isUsable(Instant now) {
        return invalidatedAt == null && expiresAt.isAfter(now);
    }

    public void invalidate(Instant now) {
        this.invalidatedAt = now;
    }
}
