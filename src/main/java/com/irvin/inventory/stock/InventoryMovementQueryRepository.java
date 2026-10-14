package com.irvin.inventory.stock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.irvin.inventory.stock.dto.InventoryMovementResponse;
import com.irvin.inventory.stock.dto.InventoryMovementResponse.Ref;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Read side of the stock ledger; writes go only through InventoryMovementRepository.
// Per-product reads use ix_inventory_movement_product_date (product_id, movement_date).
@Repository
@RequiredArgsConstructor
public class InventoryMovementQueryRepository {

    private static final String SELECT_MOVEMENT = """
            SELECT m.id, m.product_id, m.type, m.quantity, m.resulting_stock, m.reason,
                   m.purchase_id, m.sale_id, s.sale_number, u.id AS user_id, u.name AS user_name, m.movement_date
            FROM inventory_movement m
            JOIN app_user u ON u.id = m.user_id
            LEFT JOIN sale s ON s.id = m.sale_id""";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // Newest first; ties broken by id, which follows insertion order.
    public Page<InventoryMovementResponse> search(InventoryMovementSearch search, Pageable pageable) {
        // Conditions may only reference "m": the count query below has no joins.
        // Optional conditions are fixed SQL text; values always go as parameters.
        StringBuilder where = new StringBuilder(" WHERE m.product_id = :productId");
        MapSqlParameterSource params = new MapSqlParameterSource("productId", search.productId());
        if (search.type() != null) {
            where.append(" AND m.type = :type");
            params.addValue("type", search.type().name());
        }
        if (search.from() != null) {
            where.append(" AND m.movement_date >= :from");
            // PgJDBC binds OffsetDateTime as timestamptz; Instant isn't supported.
            params.addValue("from", OffsetDateTime.ofInstant(search.from(), ZoneOffset.UTC));
        }
        if (search.to() != null) {
            where.append(" AND m.movement_date < :to");
            params.addValue("to", OffsetDateTime.ofInstant(search.to(), ZoneOffset.UTC));
        }
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());

        List<InventoryMovementResponse> content = jdbcTemplate.query(
                SELECT_MOVEMENT + where + " ORDER BY m.movement_date DESC, m.id DESC LIMIT :limit OFFSET :offset",
                params, (rs, rowNum) -> map(rs));
        // Skips the count query when the first page already holds every row.
        return PageableExecutionUtils.getPage(content, pageable, () -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_movement m" + where, params, Long.class));
    }

    public Optional<InventoryMovementResponse> findById(Long id) {
        return findOne(" WHERE m.id = :value", id);
    }

    public Optional<InventoryMovementResponse> findByIdempotencyKey(UUID idempotencyKey) {
        return findOne(" WHERE m.idempotency_key = :value", idempotencyKey);
    }

    private Optional<InventoryMovementResponse> findOne(String where, Object value) {
        return jdbcTemplate.query(SELECT_MOVEMENT + where, new MapSqlParameterSource("value", value),
                (rs, rowNum) -> map(rs)).stream().findFirst();
    }

    private static InventoryMovementResponse map(ResultSet rs) throws SQLException {
        return new InventoryMovementResponse(
                rs.getLong("id"),
                rs.getLong("product_id"),
                MovementType.valueOf(rs.getString("type")),
                rs.getInt("quantity"),
                rs.getInt("resulting_stock"),
                rs.getString("reason"),
                rs.getObject("purchase_id", Long.class),
                rs.getObject("sale_id", Long.class),
                rs.getObject("sale_number", Long.class),
                new Ref(rs.getLong("user_id"), rs.getString("user_name")),
                rs.getObject("movement_date", OffsetDateTime.class).toInstant());
    }
}
