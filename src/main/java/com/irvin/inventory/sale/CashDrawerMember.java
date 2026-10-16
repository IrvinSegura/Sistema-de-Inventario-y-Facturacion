package com.irvin.inventory.sale;

import java.io.Serializable;
import java.time.LocalDate;

import com.irvin.inventory.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// A user who sells from a drawer. Copies the drawer's day and open state, so the database enforces
// one drawer per user and day and one open drawer per user.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashDrawerMember {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("cashDrawerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_drawer_id")
    private CashDrawer cashDrawer;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private boolean drawerOpen;

    CashDrawerMember(CashDrawer cashDrawer, User user) {
        this.cashDrawer = cashDrawer;
        this.user = user;
        this.businessDate = cashDrawer.getBusinessDate();
        this.drawerOpen = true;
    }

    void drawerClosed() {
        drawerOpen = false;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        private Long cashDrawerId;
        private Long userId;
    }
}
