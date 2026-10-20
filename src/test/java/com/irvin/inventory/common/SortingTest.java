package com.irvin.inventory.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.irvin.inventory.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SortingTest {

    private static final Sort DEFAULT_SORT = Sort.by("name", "id");
    private static final Map<String, String> SORTABLE = Map.of("name", "name", "stock", "stock", "category", "category.name");

    @Test
    void unsortedRequestsGetTheDefaultOrder() {
        Pageable pageable = Sorting.apply(PageRequest.of(2, 50), SORTABLE, DEFAULT_SORT);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    void allowedPropertiesMapToTheirPathAndKeepTheDefaultAsTieBreaker() {
        Sort sort = Sorting.resolve(Sort.by(Sort.Order.desc("stock"), Sort.Order.asc("category")), SORTABLE, DEFAULT_SORT);

        assertThat(sort).containsExactly(
                Sort.Order.desc("stock"), Sort.Order.asc("category.name"), Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    void aRequestedDefaultPropertyIsNotRepeated() {
        Sort sort = Sorting.resolve(Sort.by(Sort.Order.desc("name")), SORTABLE, DEFAULT_SORT);

        assertThat(sort).containsExactly(Sort.Order.desc("name"), Sort.Order.asc("id"));
    }

    @Test
    void otherPropertiesAreRejected() {
        assertThatThrownBy(() -> Sorting.resolve(Sort.by("passwordHash"), SORTABLE, DEFAULT_SORT))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown sort property: passwordHash. Allowed: category, name, stock");
    }
}
