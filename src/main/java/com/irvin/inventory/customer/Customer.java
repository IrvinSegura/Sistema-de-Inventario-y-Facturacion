package com.irvin.inventory.customer;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    // Generic tax ID mandated by SAT for walk-in sales; the customer is seeded by V2.
    public static final String WALK_IN_TAX_ID = "XAXX010101000";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 13)
    private String taxId;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    private String address;

    @Column(nullable = false)
    private boolean active = true;

    @Setter(AccessLevel.NONE)
    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public boolean isWalkIn() {
        return WALK_IN_TAX_ID.equals(taxId);
    }
}
