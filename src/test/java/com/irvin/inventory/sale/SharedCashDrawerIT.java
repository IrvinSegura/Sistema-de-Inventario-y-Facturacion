package com.irvin.inventory.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.irvin.inventory.AbstractIntegrationTest;
import com.irvin.inventory.config.StoreProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Drawers a manager opens for one or several users.
class SharedCashDrawerIT extends AbstractIntegrationTest {

    private static final String ASSIGNED = CASH_DRAWERS + "/assigned";
    private static final String ASSIGNABLE = CASH_DRAWERS + "/assignable-users";

    @Autowired
    private StoreProperties storeProperties;

    @Test
    void aSharedDrawerCountsEveryMembersSalesAndOnlyItsResponsibleClosesIt() throws Exception {
        CreatedUser supervisor = createUser("SUPERVISOR");
        String supervisorToken = loginOk(supervisor.email(), DEFAULT_PASSWORD).accessToken();
        CreatedUser sellerA = createSeller();
        CreatedUser sellerB = createSeller();
        String tokenA = loginOk(sellerA.email(), DEFAULT_PASSWORD).accessToken();
        String tokenB = loginOk(sellerB.email(), DEFAULT_PASSWORD).accessToken();
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 10);
        assertThat(assignableIds(supervisorToken)).contains(sellerA.id(), sellerB.id());

        Long drawerId = readId(postJson(ASSIGNED, assignedBody("300", sellerA.id(), List.of(sellerA.id(), sellerB.id())),
                supervisorToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsible.id").value(sellerA.id()))
                .andExpect(jsonPath("$.openedBy.id").value(supervisor.id()))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.openingFloat").value(300.0))
                .andExpect(jsonPath("$.businessDate").value(today().toString())));
        assertThat(assignableIds(supervisorToken)).doesNotContain(sellerA.id(), sellerB.id());
        getJson(CASH_DRAWERS + "/current", tokenB)
                .andExpect(jsonPath("$.id").value(drawerId))
                .andExpect(jsonPath("$.responsible.id").value(sellerA.id()));
        postJson(CASH_DRAWERS, "{\"openingFloat\": 0}", tokenA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Your cash drawer is already open"));

        sell(saleBody(product, 2, "CASH"), tokenA).andExpect(status().isCreated());
        Long refunded = readId(sell(saleBody(product, 1, "CASH"), tokenB).andExpect(status().isCreated()));
        sell(saleBody(product, 1, "CARD"), tokenB).andExpect(status().isCreated());
        postJson("/api/v1/sales/" + refunded + "/cancellation", "{\"reason\": \"Returned\"}", adminToken)
                .andExpect(status().isOk());

        getJson("/api/v1/sales?cashDrawerId=" + drawerId, supervisorToken)
                .andExpect(jsonPath("$.content.length()").value(3));
        // A member only gets their own tickets.
        getJson("/api/v1/sales?cashDrawerId=" + drawerId, tokenB)
                .andExpect(jsonPath("$.content.length()").value(2));

        // 300 + 20 + 10 - 10.
        String closingUrl = "/api/v1/sale-reports/cash-closing?drawerId=" + drawerId;
        String closing = getJson(closingUrl, supervisorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingFloat").value(300.0))
                .andExpect(jsonPath("$.expectedCash").value(320.0))
                .andExpect(jsonPath("$.bySeller.length()").value(2))
                .andExpect(jsonPath("$.drawers[0].members.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Number>>read(closing, "$.bySeller[?(@.seller.id == " + sellerB.id() + ")].cash.net"))
                .singleElement().satisfies(net -> assertThat(net.doubleValue()).isZero());
        assertThat(JsonPath.<List<Number>>read(closing, "$.bySeller[?(@.seller.id == " + sellerB.id() + ")].total.net"))
                .singleElement().satisfies(net -> assertThat(net.doubleValue()).isEqualTo(10.0));

        // Blind count for every member; only the responsible (or a manager) closes it.
        getJson(closingUrl, tokenB).andExpect(status().isConflict());
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("320"), tokenB)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the drawer's responsible or a manager can close it"));
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("319"), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closing.closedBy.id").value(sellerA.id()))
                .andExpect(jsonPath("$.closing.expectedCash").value(320.0))
                .andExpect(jsonPath("$.closing.difference").value(-1.0));

        sell(saleBody(product, 1, "CASH"), tokenB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Your cash drawer for today is closed"));
        getJson(CASH_DRAWERS + "/current", tokenB).andExpect(status().isNotFound());
        getJson(closingUrl, tokenB).andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(true));
        postJson(CASH_DRAWERS, "{\"openingFloat\": 0}", tokenB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Today's cash drawer is already closed"));
        // Every member finds it in their history and their day.
        getJson(CASH_DRAWERS, tokenB).andExpect(jsonPath("$.content[0].id").value(drawerId));
        getJson(CASH_DRAWERS + "?userId=" + sellerB.id(), adminToken)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].members.length()").value(2));
        getJson("/api/v1/sale-reports/cash-closing", tokenB)
                .andExpect(jsonPath("$.drawers.length()").value(1))
                .andExpect(jsonPath("$.drawers[0].id").value(drawerId));
    }

    @Test
    void aManagerOpensAndClosesADrawerForOneSeller() throws Exception {
        CreatedUser seller = createSeller();
        String token = loginOk(seller.email(), DEFAULT_PASSWORD).accessToken();
        String adminToken = adminAccessToken();
        Long product = createPricedProduct("10.00", "0");
        addStock(product, 1);

        Long drawerId = readId(postJson(ASSIGNED, assignedBody("50", seller.id(), List.of(seller.id())), adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members[0].id").value(seller.id())));
        sell(saleBody(product, 1, "CASH"), token).andExpect(status().isCreated());
        // The manager isn't a member, but can still close it.
        postJson(CASH_DRAWERS + "/" + drawerId + "/closing", closeBody("60"), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closing.expectedCash").value(60.0))
                .andExpect(jsonPath("$.closing.difference").value(0.0));
    }

    @Test
    void assignedDrawersRejectInvalidRequests() throws Exception {
        String adminToken = adminAccessToken();
        String sellerToken = sellerToken();
        CreatedUser free = createSeller();
        CreatedUser other = createSeller();

        postJson(ASSIGNED, assignedBody("0", free.id(), List.of(free.id())), sellerToken)
                .andExpect(status().isForbidden());
        getJson(ASSIGNABLE, sellerToken).andExpect(status().isForbidden());

        postJson(ASSIGNED, assignedBody("0", other.id(), List.of(free.id())), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The responsible must be one of the assigned users"));
        postJson(ASSIGNED, assignedBody("0", free.id(), List.of(free.id(), free.id())), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Each user can be assigned only once"));
        postJson(ASSIGNED, assignedBody("0", 999999999L, List.of(free.id(), 999999999L)), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("User not found: 999999999"));
        for (String body : List.of("{}", assignedBody("-1", free.id(), List.of(free.id())),
                assignedBody("0", free.id(), List.of()))) {
            postJson(ASSIGNED, body, adminToken).andExpect(status().isBadRequest());
        }
        jdbcTemplate.update("UPDATE app_user SET active = false WHERE id = ?", other.id());
        postJson(ASSIGNED, assignedBody("0", free.id(), List.of(free.id(), other.id())), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("User is inactive: Test User"));

        CreatedUser selling = createSeller();
        openDrawer(loginOk(selling.email(), DEFAULT_PASSWORD).accessToken(), "0");
        postJson(ASSIGNED, assignedBody("0", free.id(), List.of(free.id(), selling.id())), adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Test User is already in an open cash drawer"));
        CreatedUser stale = createSeller();
        LocalDate yesterday = today().minusDays(1);
        insertDrawer(stale.id(), yesterday, "0", true);
        postJson(ASSIGNED, assignedBody("0", stale.id(), List.of(stale.id())), adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Test User must close the cash drawer of " + yesterday + " first"));
        CreatedUser done = createSeller();
        insertDrawer(done.id(), today(), "0", false);
        postJson(ASSIGNED, assignedBody("0", done.id(), List.of(done.id())), adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Test User already had a cash drawer today"));
        // Nothing was opened for the free seller along the way.
        assertThat(assignableIds(adminToken)).contains(free.id()).doesNotContain(other.id(), selling.id(), stale.id());
    }

    @Test
    void concurrentAssignmentsOfTheSameSellerOpenOneDrawer() throws Exception {
        String adminToken = adminAccessToken();
        CreatedUser seller = createSeller();
        String body = assignedBody("0", seller.id(), List.of(seller.id()));

        List<Integer> statuses = runConcurrently(Collections.nCopies(5,
                () -> httpStatus(postJson(ASSIGNED, body, adminToken))));

        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cash_drawer_member WHERE user_id = ?",
                Long.class, seller.id())).isEqualTo(1);
    }

    private List<Long> assignableIds(String token) throws Exception {
        String body = getJson(ASSIGNABLE, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(body, "$[*].id").stream().map(Number::longValue).toList();
    }

    private LocalDate today() {
        return LocalDate.now(storeProperties.timeZone());
    }

    private static String assignedBody(String openingFloat, Long responsibleId, List<Long> memberIds) {
        return """
                {"openingFloat": %s, "responsibleId": %d, "memberIds": [%s]}
                """.formatted(openingFloat, responsibleId,
                memberIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
    }

    private static String closeBody(String countedCash) {
        return """
                {"countedCash": %s}
                """.formatted(countedCash);
    }

    private static String saleBody(Long productId, int quantity, String paymentMethod) {
        return """
                {"paymentMethod": "%s", "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(paymentMethod, productId, quantity);
    }
}
