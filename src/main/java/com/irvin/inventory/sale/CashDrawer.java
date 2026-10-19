package com.irvin.inventory.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

// A drawer for one local day, used by one or more members. Changes only by open() and close(): the
// closing data is a snapshot of what was counted, so it is never edited afterwards.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashDrawer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Counts and closes it; always a member.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsible_id", updatable = false)
    private User responsible;

    // The member themselves for a personal drawer, the manager who handed over the float otherwise.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", updatable = false)
    private User openedBy;

    @OneToMany(mappedBy = "cashDrawer", cascade = CascadeType.PERSIST)
    private List<CashDrawerMember> members = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal openingFloat;

    @Column(nullable = false, updatable = false)
    private Instant openedAt;

    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal countedCash;

    private String closingNote;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // The responsible must be one of the members.
    public static CashDrawer open(User openedBy, User responsible, List<User> members, LocalDate businessDate,
            BigDecimal openingFloat, Instant openedAt) {
        CashDrawer drawer = new CashDrawer();
        drawer.openedBy = openedBy;
        drawer.responsible = responsible;
        drawer.businessDate = businessDate;
        drawer.openingFloat = openingFloat;
        drawer.openedAt = openedAt;
        members.forEach(member -> drawer.members.add(new CashDrawerMember(drawer, member)));
        return drawer;
    }

    void close(User closedBy, Instant closedAt, BigDecimal expectedCash, BigDecimal countedCash, String note) {
        if (!isOpen()) {
            throw new IllegalStateException("Cash drawer " + id + " is already closed");
        }
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.expectedCash = expectedCash;
        this.countedCash = countedCash;
        this.closingNote = note;
        members.forEach(CashDrawerMember::drawerClosed);
    }

    public boolean isOpen() {
        return closedAt == null;
    }
}
