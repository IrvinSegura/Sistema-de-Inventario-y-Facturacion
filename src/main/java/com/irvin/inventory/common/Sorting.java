package com.irvin.inventory.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.irvin.inventory.common.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Client-chosen order for list endpoints, limited to the properties each list allows: API name to
// entity path (e.g. "category" -> "category.name"). Anything else is rejected, so a client can't order
// by sensitive or unindexed columns. The list's default order follows as a tie-breaker, which keeps
// pages stable when many rows share a value.
public final class Sorting {

    private Sorting() {
    }

    public static Pageable apply(Pageable pageable, Map<String, String> allowed, Sort defaultSort) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                resolve(pageable.getSort(), allowed, defaultSort));
    }

    static Sort resolve(Sort requested, Map<String, String> allowed, Sort defaultSort) {
        if (requested.isUnsorted()) {
            return defaultSort;
        }
        List<Sort.Order> orders = new ArrayList<>();
        Set<String> used = new TreeSet<>();
        for (Sort.Order order : requested) {
            String path = allowed.get(order.getProperty());
            if (path == null) {
                throw new BadRequestException("Unknown sort property: " + order.getProperty()
                        + ". Allowed: " + String.join(", ", new TreeSet<>(allowed.keySet())));
            }
            if (used.add(path)) {
                orders.add(new Sort.Order(order.getDirection(), path));
            }
        }
        defaultSort.stream().filter(order -> !used.contains(order.getProperty())).forEach(orders::add);
        return Sort.by(orders);
    }
}
