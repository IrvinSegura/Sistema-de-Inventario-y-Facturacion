package com.irvin.inventory.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.irvin.inventory.customer.Customer;
import com.irvin.inventory.report.dto.CustomerSalesResponse;
import com.irvin.inventory.report.dto.LowStockProductResponse.CategoryRef;
import com.irvin.inventory.report.dto.ProductSalesResponse;
import com.irvin.inventory.report.dto.SaleReportResponse.DailyTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.PaymentMethodTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.ProductTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.SellerTotal;
import com.irvin.inventory.report.dto.SaleReportResponse.Totals;
import com.irvin.inventory.sale.PaymentMethod;
import com.irvin.inventory.sale.SaleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Aggregations in SQL: summing in Java would load every sale of the period.
// Sale date ranges use ix_sale_date (ix_sale_user_date by seller); cancellation ranges use the
// partial ix_sale_cancelled_at, with the seller as a filter (cancellations are few). A drawer's sales
// and refunds use ix_sale_cash_drawer and ix_sale_cancellation_drawer.
@Repository
@RequiredArgsConstructor
public class SaleReportRepository {

    private static final int TOP_PRODUCTS_LIMIT = 10;
    private static final int MONEY_SCALE = 2;
    private static final String COMPLETED_ONLY = " AND s.status = :completed";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);
    // Same category filter for every product query; CAST so a null id still has a type.
    private static final String CATEGORY_FILTER =
            " AND (CAST(:categoryId AS bigint) IS NULL OR p.category_id = :categoryId)";
    // The last sale is looked up only for the page's rows, walking ix_sale_item_product_sale backwards
    // until the first completed one, so it costs the same however long the product's history is. Sale
    // ids follow sale dates (only concurrent sales can swap by milliseconds), close enough for a date.
    private static final String PRODUCT_COLUMNS = """
            p.id, p.sku, p.name, p.active, p.stock, p.average_cost, c.id AS category_id,
            c.name AS category_name,
            (SELECT ls.sale_date FROM sale_item lsi JOIN sale ls ON ls.id = lsi.sale_id
             WHERE lsi.product_id = p.id AND ls.status = :completed
             ORDER BY lsi.sale_id DESC LIMIT 1) AS last_sale_date,
            count(*) OVER () AS total_rows""";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // The only date columns a period can filter on; an enum so no other text can reach the SQL.
    public enum DateColumn {
        SALE_DATE("s.sale_date"),
        CANCELLED_AT("s.cancelled_at");

        private final String sql;

        DateColumn(String sql) {
            this.sql = sql;
        }
    }

    // Count and total of sales per payment method.
    public record PaymentMethodAmount(PaymentMethod paymentMethod, long count, BigDecimal total) {
    }

    // Local days [from, toExclusive) in the store's zone, optionally restricted to one seller.
    public record Period(LocalDate from, LocalDate toExclusive, ZoneId zone, Long sellerId) {

        // PostgreSQL converts both the bounds and each row's day with the same zone rules, so a sale
        // can never pass the filter yet fall outside the daily breakdown. The bounds are constants
        // per query, so the date indexes still apply. Values always go as parameters.
        String where(DateColumn column) {
            return " WHERE " + column.sql + " >= CAST(:from AS timestamp) AT TIME ZONE :zone"
                    + " AND " + column.sql + " < CAST(:toExclusive AS timestamp) AT TIME ZONE :zone"
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

    // Count and total per seller of the sale and payment method; combinations without sales are missing.
    public record SellerPaymentAmount(long sellerId, String sellerName, PaymentMethod paymentMethod, long count,
            BigDecimal total) {
    }

    // Cash closing of a period: every sale rung up in it, whatever its current status, since the money
    // entered a drawer then.
    public List<SellerPaymentAmount> salesBySeller(Period period) {
        return amountsBySeller(period.where(DateColumn.SALE_DATE), period.params());
    }

    // Cash closing of a period: cancellations made in it, whatever the sale's date, since the money left
    // a drawer then. By the seller of the sale, whoever cancelled it.
    public List<SellerPaymentAmount> cancellationsBySeller(Period period) {
        return amountsBySeller(period.where(DateColumn.CANCELLED_AT), period.params());
    }

    // Cash closing of a drawer: the sales recorded in it, whatever their dates and current status.
    // Keep both in sync with SaleRepository.drawerNetCash, which closes the drawer with the same rule.
    public List<SellerPaymentAmount> drawerSalesBySeller(long drawerId) {
        return amountsBySeller(" WHERE s.cash_drawer_id = :drawerId", drawerParams(drawerId));
    }

    // Cash closing of a drawer: the cancellations whose refund came out of it.
    public List<SellerPaymentAmount> drawerCancellationsBySeller(long drawerId) {
        return amountsBySeller(" WHERE s.cancellation_drawer_id = :drawerId", drawerParams(drawerId));
    }

    private List<SellerPaymentAmount> amountsBySeller(String where, MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT s.user_id, u.name, s.payment_method, count(*) AS sale_count, sum(s.total) AS total
                FROM sale s JOIN app_user u ON u.id = s.user_id""" + where + """

                GROUP BY s.user_id, u.name, s.payment_method
                ORDER BY u.name, s.user_id, s.payment_method""",
                params,
                (rs, rowNum) -> new SellerPaymentAmount(rs.getLong("user_id"), rs.getString("name"),
                        PaymentMethod.valueOf(rs.getString("payment_method")), rs.getLong("sale_count"),
                        money(rs.getBigDecimal("total"))));
    }

    private static MapSqlParameterSource drawerParams(long drawerId) {
        return new MapSqlParameterSource().addValue("drawerId", drawerId);
    }

    private List<PaymentMethodAmount> amountsByPaymentMethod(String where, Period period) {
        return jdbcTemplate.query("""
                SELECT s.payment_method, count(*) AS sale_count, sum(s.total) AS total
                FROM sale s""" + where + " GROUP BY s.payment_method ORDER BY s.payment_method",
                period.params(),
                (rs, rowNum) -> new PaymentMethodAmount(PaymentMethod.valueOf(rs.getString("payment_method")),
                        rs.getLong("sale_count"), money(rs.getBigDecimal("total"))));
    }

    // Statuses without sales are missing from the map.
    public Map<SaleStatus, Totals> totalsByStatus(Period period) {
        List<Map.Entry<SaleStatus, Totals>> rows = jdbcTemplate.query("""
                SELECT s.status, count(*) AS sale_count,
                       sum(s.subtotal) AS subtotal, sum(s.tax) AS tax, sum(s.total) AS total
                FROM sale s""" + period.where(DateColumn.SALE_DATE) + " GROUP BY s.status",
                period.params(),
                (rs, rowNum) -> Map.entry(SaleStatus.valueOf(rs.getString("status")), new Totals(
                        rs.getLong("sale_count"), money(rs.getBigDecimal("subtotal")),
                        money(rs.getBigDecimal("tax")), money(rs.getBigDecimal("total")))));
        Map<SaleStatus, Totals> totals = new EnumMap<>(SaleStatus.class);
        rows.forEach(entry -> totals.put(entry.getKey(), entry.getValue()));
        return totals;
    }

    public List<PaymentMethodTotal> completedByPaymentMethod(Period period) {
        return amountsByPaymentMethod(period.where(DateColumn.SALE_DATE) + COMPLETED_ONLY, period).stream()
                .map(amount -> new PaymentMethodTotal(amount.paymentMethod(), amount.count(), amount.total()))
                .toList();
    }

    public List<SellerTotal> completedBySeller(Period period) {
        return jdbcTemplate.query("""
                SELECT u.id, u.name, count(*) AS sale_count, sum(s.total) AS total
                FROM sale s JOIN app_user u ON u.id = s.user_id""" + period.where(DateColumn.SALE_DATE) + COMPLETED_ONLY
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
                    JOIN sale_item si ON si.sale_id = s.id"""
                        + period.where(DateColumn.SALE_DATE) + COMPLETED_ONLY + """

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
                FROM sale s""" + period.where(DateColumn.SALE_DATE) + COMPLETED_ONLY + " GROUP BY day ORDER BY day",
                period.params(),
                (rs, rowNum) -> new DailyTotal(rs.getObject("day", LocalDate.class),
                        rs.getLong("sale_count"), money(rs.getBigDecimal("total"))));
        return days.stream().collect(Collectors.toMap(DailyTotal::date, Function.identity()));
    }

    // Completed lines with a known cost: what they sold for (before VAT) and what the units cost.
    public record CostTotals(BigDecimal costedSubtotal, BigDecimal cost) {
    }

    public CostTotals completedCost(Period period) {
        return jdbcTemplate.queryForObject("""
                SELECT coalesce(sum(si.subtotal) FILTER (WHERE si.unit_cost IS NOT NULL), 0) AS costed_subtotal,
                       coalesce(sum(si.quantity * si.unit_cost), 0) AS cost
                FROM sale s JOIN sale_item si ON si.sale_id = s.id""" + period.where(DateColumn.SALE_DATE) + COMPLETED_ONLY,
                period.params(),
                (rs, rowNum) -> new CostTotals(money(rs.getBigDecimal("costed_subtotal")),
                        cost(rs.getBigDecimal("cost"))));
    }

    // Completed lines of the period by product. Unit costs have four decimals; the sums are rounded once.
    private static String soldProducts(Period period) {
        return """
                WITH sold AS (
                    SELECT si.product_id, sum(si.quantity) AS quantity, sum(si.subtotal) AS subtotal,
                           sum(si.quantity * si.unit_cost) AS cost, bool_and(si.unit_cost IS NOT NULL) AS costed
                    FROM sale s JOIN sale_item si ON si.sale_id = s.id""" + period.where(DateColumn.SALE_DATE)
                + COMPLETED_ONLY + """

                    GROUP BY si.product_id
                )
                """;
    }

    // Best first: by amount, then by id so pages never overlap.
    public Page<ProductSalesResponse> soldProducts(Period period, Long categoryId, Pageable pageable) {
        MapSqlParameterSource params = productParams(period, categoryId);
        return page(soldProducts(period) + "SELECT " + PRODUCT_COLUMNS + """
                , sold.quantity, sold.subtotal, sold.cost, sold.costed
                FROM sold JOIN product p ON p.id = sold.product_id JOIN category c ON c.id = p.category_id
                WHERE TRUE""" + CATEGORY_FILTER + """

                ORDER BY sold.subtotal DESC, p.id
                LIMIT :limit OFFSET :offset""",
                params, pageable,
                (rs, rowNum) -> {
                    BigDecimal subtotal = money(rs.getBigDecimal("subtotal"));
                    BigDecimal cost = rs.getBoolean("costed") ? cost(rs.getBigDecimal("cost")) : null;
                    return productSales(rs, rs.getLong("quantity"), subtotal, cost,
                            cost == null ? null : subtotal.subtract(cost));
                },
                () -> jdbcTemplate.queryForObject(soldProducts(period) + """
                        SELECT count(*) FROM sold JOIN product p ON p.id = sold.product_id WHERE TRUE"""
                        + CATEGORY_FILTER, params, Long.class));
    }

    // Active products with units on hand and no completed sale in the period: stock that isn't moving.
    // Products created after the period weren't there to sell.
    private static String unsoldFilter(Period period) {
        return """
                 FROM product p JOIN category c ON c.id = p.category_id
                WHERE p.active AND p.stock > 0
                  AND p.created_at < CAST(:toExclusive AS timestamp) AT TIME ZONE :zone""" + CATEGORY_FILTER + """

                  AND NOT EXISTS (
                      SELECT 1 FROM sale_item si JOIN sale s ON s.id = si.sale_id""" + period.where(DateColumn.SALE_DATE)
                + COMPLETED_ONLY + " AND si.product_id = p.id)";
    }

    // Most units first: the most money waiting on the shelf.
    public Page<ProductSalesResponse> unsoldProducts(Period period, Long categoryId, Pageable pageable) {
        MapSqlParameterSource params = productParams(period, categoryId);
        return page("SELECT " + PRODUCT_COLUMNS + unsoldFilter(period)
                        + " ORDER BY p.stock DESC, p.id LIMIT :limit OFFSET :offset",
                params, pageable,
                (rs, rowNum) -> productSales(rs, 0, ZERO, ZERO, ZERO),
                () -> jdbcTemplate.queryForObject("SELECT count(*)" + unsoldFilter(period), params, Long.class));
    }

    private static MapSqlParameterSource productParams(Period period, Long categoryId) {
        return period.params().addValue("categoryId", categoryId);
    }

    // One query per page: its rows carry the total (count(*) OVER (), "total_rows"), so the period isn't
    // aggregated twice. Only an offset past the end, which returns no rows, needs the count apart.
    private <T> Page<T> page(String sql, MapSqlParameterSource params, Pageable pageable, RowMapper<T> mapper,
            Supplier<Long> count) {
        long[] total = {0};
        List<T> rows = jdbcTemplate.query(sql,
                new MapSqlParameterSource(params.getValues())
                        .addValue("limit", pageable.getPageSize())
                        .addValue("offset", pageable.getOffset()),
                (rs, rowNum) -> {
                    total[0] = rs.getLong("total_rows");
                    return mapper.mapRow(rs, rowNum);
                });
        if (rows.isEmpty() && pageable.getOffset() > 0) {
            total[0] = count.get();
        }
        return new PageImpl<>(rows, pageable, total[0]);
    }

    private static ProductSalesResponse productSales(ResultSet rs, long quantity, BigDecimal subtotal,
            BigDecimal cost, BigDecimal grossProfit) throws SQLException {
        Timestamp lastSale = rs.getTimestamp("last_sale_date");
        return new ProductSalesResponse(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                new CategoryRef(rs.getLong("category_id"), rs.getString("category_name")), rs.getBoolean("active"),
                rs.getInt("stock"), rs.getBigDecimal("average_cost"), quantity, subtotal, cost, grossProfit,
                lastSale == null ? null : lastSale.toInstant());
    }

    // Registered customers only: the walk-in customer would top every list without saying anything.
    private static String customerSales(Period period) {
        return " FROM sale s JOIN customer c ON c.id = s.customer_id" + period.where(DateColumn.SALE_DATE)
                + COMPLETED_ONLY + " AND c.tax_id IS DISTINCT FROM :walkInTaxId";
    }

    // Best customers first, by what they spent.
    public Page<CustomerSalesResponse> customers(Period period, Pageable pageable) {
        MapSqlParameterSource params = customerParams(period);
        return page("""
                SELECT c.id, c.name, c.tax_id, count(*) AS sale_count, sum(s.total) AS total,
                       max(s.sale_date) AS last_sale_date, count(*) OVER () AS total_rows""" + customerSales(period) + """

                GROUP BY c.id, c.name, c.tax_id
                ORDER BY total DESC, c.id
                LIMIT :limit OFFSET :offset""",
                params, pageable,
                (rs, rowNum) -> {
                    long count = rs.getLong("sale_count");
                    BigDecimal total = money(rs.getBigDecimal("total"));
                    return new CustomerSalesResponse(rs.getLong("id"), rs.getString("name"), rs.getString("tax_id"),
                            count, total, total.divide(BigDecimal.valueOf(count), MONEY_SCALE, RoundingMode.HALF_UP),
                            rs.getTimestamp("last_sale_date").toInstant());
                },
                () -> jdbcTemplate.queryForObject("SELECT count(DISTINCT s.customer_id)" + customerSales(period),
                        params, Long.class));
    }

    private static MapSqlParameterSource customerParams(Period period) {
        return period.params().addValue("walkInTaxId", Customer.WALK_IN_TAX_ID);
    }

    // Quantities times four-decimal unit costs: rounded to cents once, after summing.
    private static BigDecimal cost(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
