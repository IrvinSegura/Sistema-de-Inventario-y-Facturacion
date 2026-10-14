package com.irvin.inventory.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.irvin.inventory.customer.Customer;
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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.generator.EventType;

// Never edited or deleted; the only change is cancellation, done by SaleRepository.cancel.
// @Immutable makes Hibernate skip dirty checking, so a stray setter can't rewrite a recorded sale.
@Entity
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Assigned by sale_number_seq on insert and read back by Hibernate.
    @Setter(AccessLevel.NONE)
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Long saleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant saleDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    // Status and cancellation data change only through the atomic UPDATE in SaleRepository.cancel.
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SaleStatus status = SaleStatus.COMPLETED;

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

    @Column(nullable = false)
    private UUID idempotencyKey;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<SaleItem> items = new ArrayList<>();

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }
}
