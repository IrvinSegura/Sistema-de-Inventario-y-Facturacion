package com.irvin.inventory.sale;

import java.net.URI;
import java.time.LocalDate;

import com.irvin.inventory.sale.dto.CashDrawerResponse;
import com.irvin.inventory.sale.dto.CloseCashDrawerRequest;
import com.irvin.inventory.sale.dto.OpenCashDrawerRequest;
import com.irvin.inventory.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cash drawers", description = "Any user opens and closes their own drawer; ADMIN sees and closes any")
@RestController
@RequestMapping(CashDrawerController.PATH)
@RequiredArgsConstructor
public class CashDrawerController {

    static final String PATH = "/api/v1/cash-drawers";

    private final CashDrawerService cashDrawerService;

    @Operation(summary = "Open today's cash drawer with its opening float",
            description = "One drawer per user and day in the store's time zone; sales need it open. 409 if a "
                    + "drawer is still open (close the previous day's first) or today's was already closed.")
    @PostMapping
    public ResponseEntity<CashDrawerResponse> open(
            @Valid @RequestBody OpenCashDrawerRequest request, @AuthenticationPrincipal Jwt jwt) {
        CashDrawerResponse drawer = cashDrawerService.open(request, CurrentUser.from(jwt).id());
        return ResponseEntity.created(URI.create(PATH + "/" + drawer.id())).body(drawer);
    }

    @Operation(summary = "The current user's open cash drawer", description = "404 if none is open.")
    @GetMapping("/current")
    public CashDrawerResponse findCurrent(@AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.findCurrent(CurrentUser.from(jwt).id());
    }

    @Operation(summary = "List cash drawers, newest day first", description = "Sellers only get their own.")
    @GetMapping
    public Page<CashDrawerResponse> search(
            @Parameter(description = "Ignored for sellers") @RequestParam(required = false) Long userId,
            @Parameter(description = "First business day (inclusive), e.g. 2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last business day (inclusive), e.g. 2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.search(new CashDrawerSearch(userId, from, to), pageable, CurrentUser.from(jwt));
    }

    @Operation(summary = "Get a cash drawer")
    @GetMapping("/{id}")
    public CashDrawerResponse findById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.findById(id, CurrentUser.from(jwt));
    }

    @Operation(summary = "Close a cash drawer with the counted cash",
            description = "Records the expected cash (opening float + cash sales of the day - cash "
                    + "cancellations of the day) and the difference with the counted cash. Waits for sales "
                    + "in progress; afterwards the user can't sell until the next day's drawer is opened. "
                    + "Already closed: 409.")
    @PostMapping("/{id}/closing")
    public CashDrawerResponse close(
            @PathVariable Long id,
            @Valid @RequestBody CloseCashDrawerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.close(id, request, CurrentUser.from(jwt));
    }
}
