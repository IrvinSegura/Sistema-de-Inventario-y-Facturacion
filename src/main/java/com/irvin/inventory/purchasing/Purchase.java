package com.irvin.inventory.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.irvin.inventory.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Never edited or deleted once recorded; it can only be cancelled.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant purchaseDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // Status and cancellation data change only through the atomic UPDATE in PurchaseRepository.cancel.
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PurchaseStatus status = PurchaseStatus.COMPLETED;

    @Setter(AccessLevel.NONE)
    @Column(updatable = false)
    private Instant cancelledAt;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", updatable = false)
    private User cancelledBy;

    @Setter(AccessLevel.NONE)
    @Column(updatable = false)
    private String cancellationReason;

    private UUID idempotencyKey;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<PurchaseItem> items = new ArrayList<>();

    public void addItem(PurchaseItem item) {
        item.setPurchase(this);
        items.add(item);
    }
}
