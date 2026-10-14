package com.irvin.inventory.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.irvin.inventory.report.dto.SaleReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.PaymentMethodTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.ProductTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.SellerTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.Totals;
import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.SaleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Aggregations in SQL: summing in Java would load every sale of the period.
// Range filters use ix_sale_date, or ix_sale_user_date when filtered by seller.
@Repository
@RequiredArgsConstructor
public class SaleReportRepository {

    private static final int TOP_PRODUCTS_LIMIT = 10;
    private static final int MONEY_SCALE = 2;
    private static final String COMPLETED_ONLY = " AND s.status = :completed";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // Local days [from, toExclusive) in the store's zone, optionally restricted to one seller.
    public record Period(LocalDate from, LocalDate toExclusive, ZoneId zone, Long sellerId) {

        // PostgreSQL converts both the bounds and each sale's day with the same zone rules,
        // so a sale can never pass the filter yet fall outside the daily breakdown.
        // The bounds are constants per query, so ix_sale_date / ix_sale_user_date still apply.
        // The seller condition is fixed SQL text; values always go as parameters.
        String where() {
            return " WHERE s.sale_date >= CAST(:from AS timestamp) AT TIME ZONE :zone"
                    + " AND s.sale_date < CAST(:toExclusive AS timestamp) AT TIME ZONE :zone"
                    + (sellerId == null ? "" : " AND s.user_id = :sellerId");
        }

        MapSqlParameterSource params() {
            return new MapSqlParameterSource()
                    .addValue("from", from)
                    .addValue("toExclusive", toExclusive)
                    .addValue("zone", zone.getId())
                    .addValue("sellerId", sellerId)
                    // From the enum, so renaming a status can't silently break the filter.
                    .addValue("completed", SaleStatus.COMPLETED.name());
        }
    }

    // Statuses without sales are missing from the map.
    public Map<SaleStatus, Totals> totalsByStatus(Period period) {
        List<Map.Entry<SaleStatus, Totals>> rows = jdbcTemplate.query("""
                SELECT s.status, count(*) AS sale_count,
                       sum(s.subtotal) AS subtotal, sum(s.tax) AS tax, sum(s.total) AS total
                FROM sale s""" + period.where() + " GROUP BY s.status",
                period.params(),
                (rs, rowNum) -> Map.entry(SaleStatus.valueOf(rs.getString("status")), new Totals(
                        rs.getLong("sale_count"), money(rs.getBigDecimal("subtotal")),
                        money(rs.getBigDecimal("tax")), money(rs.getBigDecimal("total")))));
        Map<SaleStatus, Totals> totals = new EnumMap<>(SaleStatus.class);
        rows.forEach(entry -> totals.put(entry.getKey(), entry.getValue()));
        return totals;
    }

    public List<PaymentMethodTotal> completedByPaymentMethod(Period period) {
        return jdbcTemplate.query("""
                SELECT s.payment_method, count(*) AS sale_count, sum(s.total) AS total
                FROM sale s""" + period.where() + COMPLETED_ONLY
                        + " GROUP BY s.payment_method ORDER BY s.payment_method",
                period.params(),
                (rs, rowNum) -> new PaymentMethodTotal(PaymentMethod.valueOf(rs.getString("payment_method")),
                        rs.getLong("sale_count"), money(rs.getBigDecimal("total"))));
    }

    public List<SellerTotal> completedBySeller(Period period) {
        return jdbcTemplate.query("""
                SELECT u.id, u.name, count(*) AS sale_count, sum(s.total) AS total
                FROM sale s JOIN app_user u ON u.id = s.user_id""" + period.where() + COMPLETED_ONLY
                        + " GROUP BY u.id, u.name ORDER BY total DESC, u.id",
                period.params(),
                (rs, rowNum) -> new SellerTotal(rs.getLong("id"), rs.getString("name"),
                        rs.getLong("sale_count"), money(rs.getBigDecimal("total"))));
    }

    // Best sellers by units; current SKU and name, since products can be renamed.
    // Aggregates and cuts to the top N first, so only N rows are joined with product.
    public List<ProductTotal> topProducts(Period period) {
        return jdbcTemplate.query("""
                SELECT p.id, p.sku, p.name, t.quantity, t.subtotal
                FROM (
                    SELECT si.product_id, sum(si.quantity) AS quantity, sum(si.subtotal) AS subtotal
                    FROM sale s
                    JOIN sale_item si ON si.sale_id = s.id""" + period.where() + COMPLETED_ONLY + """

                    GROUP BY si.product_id
                    ORDER BY quantity DESC, si.product_id
                    LIMIT :limit
                ) t
                JOIN product p ON p.id = t.product_id
                ORDER BY t.quantity DESC, p.id""",
                period.params().addValue("limit", TOP_PRODUCTS_LIMIT),
                (rs, rowNum) -> new ProductTotal(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                        rs.getLong("quantity"), money(rs.getBigDecimal("subtotal"))));
    }

    // Days without sales are missing from the map.
    public Map<LocalDate, DailyTotal> completedByDay(Period period) {
        List<DailyTotal> days = jdbcTemplate.query("""
                SELECT CAST(s.sale_date AT TIME ZONE :zone AS date) AS day, count(*) AS sale_count,
                       sum(s.total) AS total
                FROM sale s""" + period.where() + COMPLETED_ONLY + " GROUP BY day ORDER BY day",
                period.params(),
                (rs, rowNum) -> new DailyTotal(rs.getObject("day", LocalDate.class),
                        rs.getLong("sale_count"), money(rs.getBigDecimal("total"))));
        return days.stream().collect(Collectors.toMap(DailyTotal::date, Function.identity()));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
