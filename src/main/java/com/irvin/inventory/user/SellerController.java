package com.irvin.inventory.user;

import java.util.List;

import com.irvin.inventory.security.Roles;
import com.irvin.inventory.user.dto.SellerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Supervisors filter sales, drawers and reports by seller but can't list accounts (UserController).
@Tag(name = "Sellers", description = "Seller names for filters. ADMIN or SUPERVISOR")
@RestController
@RequestMapping("/api/v1/sellers")
@PreAuthorize(Roles.MANAGERS)
@RequiredArgsConstructor
public class SellerController {

    private final UserService userService;

    @Operation(summary = "Every seller account, by name", description = "Inactive ones too, for past records.")
    @GetMapping
    public List<SellerResponse> findAll() {
        return userService.findSellers();
    }
}
