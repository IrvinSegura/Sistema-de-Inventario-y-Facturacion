package com.irvin.inventory.sale;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import com.irvin.inventory.sale.dto.AssignableUserResponse;
import com.irvin.inventory.sale.dto.CashDrawerResponse;
import com.irvin.inventory.sale.dto.CloseCashDrawerRequest;
import com.irvin.inventory.sale.dto.OpenAssignedCashDrawerRequest;
import com.irvin.inventory.sale.dto.OpenCashDrawerRequest;
import com.irvin.inventory.security.CurrentUser;
import com.irvin.inventory.security.Roles;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cash drawers", description = "Any user opens and closes their own drawer; ADMIN and SUPERVISOR also "
        + "open drawers for others (shared by several users if needed), see and close any")
@RestController
@RequestMapping(CashDrawerController.PATH)
@RequiredArgsConstructor
public class CashDrawerController {

    static final String PATH = "/api/v1/cash-drawers";

    private final CashDrawerService cashDrawerService;

    @Operation(summary = "Open today's cash drawer with its opening float",
            description = "A personal drawer: the user is its only member and its responsible. Each user is in "
                    + "one drawer per day in the store's time zone; sales need it open. 409 if they're still in "
                    + "an open drawer (the previous day's must be closed first) or today's was already closed.")
    @PostMapping
    public ResponseEntity<CashDrawerResponse> open(
            @Valid @RequestBody OpenCashDrawerRequest request, @AuthenticationPrincipal Jwt jwt) {
        return created(cashDrawerService.open(request, CurrentUser.from(jwt).id()));
    }

    @Operation(summary = "Open today's cash drawer for one or several users",
            description = "The manager hands over the float; the members sell from it and the responsible (one "
                    + "of them) counts and closes it. 400 for unknown, inactive or repeated users; 409 if one of "
                    + "them is already in an open drawer or had one today.")
    @PreAuthorize(Roles.MANAGERS)
    @PostMapping("/assigned")
    public ResponseEntity<CashDrawerResponse> openAssigned(
            @Valid @RequestBody OpenAssignedCashDrawerRequest request, @AuthenticationPrincipal Jwt jwt) {
        return created(cashDrawerService.openAssigned(request, CurrentUser.from(jwt).id()));
    }

    @Operation(summary = "Users who can be assigned a drawer today",
            description = "Active users who aren't in an open drawer and had none today, by name.")
    @PreAuthorize(Roles.MANAGERS)
    @GetMapping("/assignable-users")
    public List<AssignableUserResponse> findAssignableUsers() {
        return cashDrawerService.findAssignableUsers();
    }

    @Operation(summary = "The open cash drawer the current user is in", description = "404 if none is open.")
    @GetMapping("/current")
    public CashDrawerResponse findCurrent(@AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.findCurrent(CurrentUser.from(jwt).id());
    }

    @Operation(summary = "List cash drawers, newest day first", description = "Sellers only get the ones they're in.")
    @GetMapping
    public Page<CashDrawerResponse> search(
            @Parameter(description = "Drawers this user is in; ignored for sellers")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "First business day (inclusive), e.g. 2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last business day (inclusive), e.g. 2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.search(new CashDrawerSearch(userId, from, to), pageable, CurrentUser.from(jwt));
    }

    @Operation(summary = "Get a cash drawer", description = "Sellers get 404 for drawers they aren't in.")
    @GetMapping("/{id}")
    public CashDrawerResponse findById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.findById(id, CurrentUser.from(jwt));
    }

    @Operation(summary = "Close a cash drawer with the counted cash",
            description = "Records the expected cash (opening float + cash sales recorded in the drawer - cash "
                    + "refunds that came out of it) and the difference with the counted cash. Only its "
                    + "responsible or a manager can close it (403 for the other members). Waits for sales in "
                    + "progress; afterwards its members can't sell until they're in the next day's drawer. "
                    + "Already closed: 409.")
    @PostMapping("/{id}/closing")
    public CashDrawerResponse close(
            @PathVariable Long id,
            @Valid @RequestBody CloseCashDrawerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return cashDrawerService.close(id, request, CurrentUser.from(jwt));
    }

    private static ResponseEntity<CashDrawerResponse> created(CashDrawerResponse drawer) {
        return ResponseEntity.created(URI.create(PATH + "/" + drawer.id())).body(drawer);
    }
}
