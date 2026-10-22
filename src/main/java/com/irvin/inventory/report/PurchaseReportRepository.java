package com.irvin.inventory.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.irvin.inventory.report.dto.PurchaseReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.PurchaseReportResponse.ProductTotal;
import com.irvin.inventory.report.dto.PurchaseReportResponse.SupplierTotal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Aggregations in SQL over ix_purchase_date (ix_purchase_supplier by supplier), like SaleReportRepository.
@Repository
@RequiredArgsConstructor
public class PurchaseReportRepository {

    private static final int TOP_PRODUCTS_LIMIT = 10;
    private static final int MONEY_SCALE = 2;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // Local days [from, toExclusive) in the store's zone, optionally from one supplier. Same bounds rule
    // as SaleReportRepository.Period: PostgreSQL converts the bounds and each row's day alike. Cancelled
    // purchases are left out: their units went back to the supplier.
    public record Period(LocalDate from, LocalDate toExclusive, ZoneId zone, Long supplierId) {

        String where() {
            return " WHERE pu.status = 'COMPLETED'"
                    + " AND pu.purchase_date >= CAST(:from AS timestamp) AT TIME ZONE :zone"
                    + " AND pu.purchase_date < CAST(:toExclusive AS timestamp) AT TIME ZONE :zone"
                    + (supplierId == null ? "" : " AND pu.supplier_id = :supplierId");
        }

        MapSqlParameterSource params() {
            return new MapSqlParameterSource()
                    .addValue("from", from)
                    .addValue("toExclusive", toExclusive)
                    .addValue("zone", zone.getId())
                    .addValue("supplierId", supplierId);
        }
    }

    public record Totals(long purchaseCount, BigDecimal total) {
    }

    public Totals totals(Period period) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) AS purchase_count, coalesce(sum(pu.total), 0) AS total FROM purchase pu"
                        + period.where(),
                period.params(),
                (rs, rowNum) -> new Totals(rs.getLong("purchase_count"), money(rs.getBigDecimal("total"))));
    }

    public long units(Period period) {
        return jdbcTemplate.queryForObject("""
                SELECT coalesce(sum(pi.quantity), 0)
                FROM purchase pu JOIN purchase_item pi ON pi.purchase_id = pu.id""" + period.where(),
                period.params(), Long.class);
    }

    public List<SupplierTotal> bySupplier(Period period) {
        return jdbcTemplate.query("""
                SELECT su.id, su.name, count(*) AS purchase_count, sum(pu.total) AS total
                FROM purchase pu JOIN supplier su ON su.id = pu.supplier_id""" + period.where() + """

                GROUP BY su.id, su.name
                ORDER BY total DESC, su.id""",
                period.params(),
                (rs, rowNum) -> new SupplierTotal(rs.getLong("id"), rs.getString("name"),
                        rs.getLong("purchase_count"), money(rs.getBigDecimal("total"))));
    }

    // Most money spent; current SKU and name. Cut to the top N before joining with product.
    public List<ProductTotal> topProducts(Period period) {
        return jdbcTemplate.query("""
                SELECT p.id, p.sku, p.name, t.quantity, t.subtotal
                FROM (
                    SELECT pi.product_id, sum(pi.quantity) AS quantity, sum(pi.subtotal) AS subtotal
                    FROM purchase pu JOIN purchase_item pi ON pi.purchase_id = pu.id""" + period.where() + """

                    GROUP BY pi.product_id
                    ORDER BY subtotal DESC, pi.product_id
                    LIMIT :limit
                ) t
                JOIN product p ON p.id = t.product_id
                ORDER BY t.subtotal DESC, p.id""",
                period.params().addValue("limit", TOP_PRODUCTS_LIMIT),
                (rs, rowNum) -> {
                    long quantity = rs.getLong("quantity");
                    BigDecimal subtotal = money(rs.getBigDecimal("subtotal"));
                    return new ProductTotal(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), quantity,
                            subtotal, subtotal.divide(BigDecimal.valueOf(quantity), MONEY_SCALE, RoundingMode.HALF_UP));
                });
    }

    // Days without purchases are missing from the map.
    public Map<LocalDate, DailyTotal> byDay(Period period) {
        List<DailyTotal> days = jdbcTemplate.query("""
                SELECT CAST(pu.purchase_date AT TIME ZONE :zone AS date) AS day, count(*) AS purchase_count,
                       sum(pu.total) AS total
                FROM purchase pu""" + period.where() + " GROUP BY day ORDER BY day",
                period.params(),
                (rs, rowNum) -> new DailyTotal(rs.getObject("day", LocalDate.class),
                        rs.getLong("purchase_count"), money(rs.getBigDecimal("total"))));
        return days.stream().collect(Collectors.toMap(DailyTotal::date, Function.identity()));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
