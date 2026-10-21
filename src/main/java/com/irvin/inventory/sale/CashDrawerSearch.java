package com.irvin.inventory.sale;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.irvin.inventory.common.DateBounds;
import com.irvin.inventory.common.exception.BadRequestException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

// Cash drawer filters; null values are ignored. sellerId matches any member of the drawer; open is
// true for drawers still open, false for closed ones. Business dates are inclusive local days.
// Validated on construction, so a bad range fails before any transaction or query.
public record CashDrawerSearch(Long sellerId, LocalDate from, LocalDate to, Boolean open) {

    public CashDrawerSearch {
        DateBounds.requireWithin(from, to);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
    }

    // Sellers only see the drawers they're in, whatever seller filter they sent.
    CashDrawerSearch withSeller(Long sellerId) {
        return new CashDrawerSearch(sellerId, from, to, open);
    }

    Specification<CashDrawer> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sellerId != null) {
                Subquery<Long> memberOf = query.subquery(Long.class);
                Root<CashDrawerMember> member = memberOf.from(CashDrawerMember.class);
                memberOf.select(member.get("cashDrawer").get("id"))
                        .where(cb.equal(member.get("user").get("id"), sellerId));
                predicates.add(root.get("id").in(memberOf));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("businessDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("businessDate"), to));
            }
            if (open != null) {
                predicates.add(open ? cb.isNull(root.get("closedAt")) : cb.isNotNull(root.get("closedAt")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
