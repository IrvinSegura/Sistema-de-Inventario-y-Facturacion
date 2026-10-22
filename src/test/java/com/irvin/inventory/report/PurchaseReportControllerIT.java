package com.irvin.inventory.report;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.config.StoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// The database is shared: purchases are moved to a day of the past nobody else uses.
class PurchaseReportControllerIT extends AbstractIntegrationTest {

    @Autowired
    private StoreProperties storeProperties;

    @Test
    void aggregatesThePurchasesOfThePeriod() throws Exception {
        Long rice = createPricedProduct("20", "0");
        Long soap = createPricedProduct("15", "0.16");
        Long supplier = createSupplier();
        Long other = createSupplier();
        // 10 x 4.50 + 2 x 30.00 = 105.00; then 5 x 5.00 = 25.00 from another supplier.
        Long first = purchase(supplier, """
                [{"productId": %d, "quantity": 10, "unitCost": 4.50}, {"productId": %d, "quantity": 2, "unitCost": 30}]
                """.formatted(rice, soap));
        Long second = purchase(other, """
                [{"productId": %d, "quantity": 5, "unitCost": 5}]
                """.formatted(rice));
        LocalDate day = moveToUnusedDay(first, second);
        String url = "/api/v1/purchase-reports?from=" + day + "&to=" + day.plusDays(1);

        getJson(url, adminAccessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/Mexico_City"))
                .andExpect(jsonPath("$.purchaseCount").value(2))
                .andExpect(jsonPath("$.units").value(17))
                .andExpect(jsonPath("$.total").value(130.0))
                .andExpect(jsonPath("$.bySupplier.length()").value(2))
                .andExpect(jsonPath("$.bySupplier[0].supplierId").value(supplier))
                .andExpect(jsonPath("$.bySupplier[0].total").value(105.0))
                // Rice: 15 units for 70.00 -> 4.67 each.
                .andExpect(jsonPath("$.topProducts[0].productId").value(rice))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(15))
                .andExpect(jsonPath("$.topProducts[0].subtotal").value(70.0))
                .andExpect(jsonPath("$.topProducts[0].averageUnitCost").value(4.67))
                .andExpect(jsonPath("$.byDay.length()").value(2))
                .andExpect(jsonPath("$.byDay[0].purchaseCount").value(2))
                .andExpect(jsonPath("$.byDay[1].total").value(0.0));
        getJson(url + "&supplierId=" + other, adminAccessToken())
                .andExpect(jsonPath("$.supplierId").value(other))
                .andExpect(jsonPath("$.purchaseCount").value(1))
                .andExpect(jsonPath("$.total").value(25.0));
    }

    @Test
    void leavesCancelledPurchasesOut() throws Exception {
        Long rice = createPricedProduct("20", "0");
        Long supplier = createSupplier();
        Long kept = purchase(supplier, """
                [{"productId": %d, "quantity": 2, "unitCost": 3}]
                """.formatted(rice));
        Long cancelled = purchase(supplier, """
                [{"productId": %d, "quantity": 5, "unitCost": 4}]
                """.formatted(rice));
        postJson("/api/v1/purchases/" + cancelled + "/cancellation", """
                {"reason": "Wrong supplier"}
                """, adminAccessToken()).andExpect(status().isOk());
        LocalDate day = moveToUnusedDay(kept, cancelled);

        getJson("/api/v1/purchase-reports?from=" + day + "&to=" + day, adminAccessToken())
                .andExpect(jsonPath("$.purchaseCount").value(1))
                .andExpect(jsonPath("$.units").value(2))
                .andExpect(jsonPath("$.total").value(6.0))
                .andExpect(jsonPath("$.bySupplier[0].purchaseCount").value(1))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(2))
                .andExpect(jsonPath("$.byDay[0].purchaseCount").value(1));
    }

    @Test
    void isForManagersAndValidatesThePeriod() throws Exception {
        getJson("/api/v1/purchase-reports?from=2026-01-01&to=2026-01-31", sellerToken())
                .andExpect(status().isForbidden());
        getJson("/api/v1/purchase-reports?from=2024-01-01&to=2025-01-01", adminAccessToken())
                .andExpect(status().isBadRequest());
        getJson("/api/v1/purchase-reports?from=1999-12-31&to=2000-01-01", adminAccessToken())
                .andExpect(status().isBadRequest());
    }

    private Long purchase(Long supplierId, String items) throws Exception {
        return readId(postJson("/api/v1/purchases", """
                {"supplierId": %d, "items": %s}
                """.formatted(supplierId, items), adminAccessToken())
                .andExpect(status().isCreated()));
    }

    // Noon of a random day in 2001-2009, in the store's zone.
    private LocalDate moveToUnusedDay(Long... purchaseIds) {
        LocalDate day = LocalDate.of(2001, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000));
        OffsetDateTime noon = day.atTime(12, 0).atZone(storeProperties.timeZone()).toOffsetDateTime();
        for (Long purchaseId : purchaseIds) {
            jdbcTemplate.update("UPDATE purchase SET purchase_date = ? WHERE id = ?", noon, purchaseId);
        }
        return day;
    }
}
