package com.vardhan.wallet.admin;

import com.vardhan.common.security.AuthenticatedUser;
import com.vardhan.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN only: enforced by SecurityConfig for every /api/admin/** path.
 */
@RestController
@RequestMapping("/api/admin/wallets")
@Tag(name = "Admin")
@Validated
public class AdminWalletController {

    private final AdminWalletService adminWalletService;

    public AdminWalletController(AdminWalletService adminWalletService) {
        this.adminWalletService = adminWalletService;
    }

    @GetMapping
    @Operation(summary = "List all wallets")
    public PageResponse<AdminWalletResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminWalletService.list(page, size);
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get one wallet")
    public AdminWalletResponse get(@PathVariable Long walletId) {
        return adminWalletService.get(walletId);
    }

    @PostMapping("/{walletId}/freeze")
    @Operation(summary = "Freeze a wallet: it can no longer send or receive money")
    public AdminWalletResponse freeze(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long walletId) {
        return adminWalletService.freeze(walletId, admin.email());
    }

    @PostMapping("/{walletId}/unfreeze")
    @Operation(summary = "Unfreeze a wallet")
    public AdminWalletResponse unfreeze(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long walletId) {
        return adminWalletService.unfreeze(walletId, admin.email());
    }
}
