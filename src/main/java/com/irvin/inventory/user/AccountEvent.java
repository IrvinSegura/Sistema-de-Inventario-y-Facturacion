package com.irvin.inventory.user;

import java.time.Instant;

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
import org.hibernate.annotations.Immutable;

// One change to an account, written with the change itself and never edited.
@Entity
@Immutable
@Getter
@NoArgsConstructor
public class AccountEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountEventType type;

    @Column(length = 150)
    private String oldValue;

    @Column(length = 150)
    private String newValue;

    @Column(nullable = false)
    private Instant occurredAt;

    public AccountEvent(User user, User actor, AccountEventType type, String oldValue, String newValue,
            Instant occurredAt) {
        this.user = user;
        this.actor = actor;
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }
}
