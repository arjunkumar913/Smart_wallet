package com.vardhan.wallet.web;

import com.vardhan.common.security.AuthenticatedUser;
import com.vardhan.common.web.PageResponse;
import com.vardhan.wallet.ledger.TransactionHistoryEntry;
import com.vardhan.wallet.ledger.TransactionHistoryService;
import com.vardhan.wallet.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/wallets")
@Tag(name = "Wallets")
@Validated
public class WalletController {

    private final WalletService walletService;
    private final TransactionHistoryService historyService;

    public WalletController(WalletService walletService, TransactionHistoryService historyService) {
        this.walletService = walletService;
        this.historyService = historyService;
    }

    @GetMapping("/me")
    @Operation(summary = "My wallet and balance")
    public WalletResponse myWallet(@AuthenticationPrincipal AuthenticatedUser user) {
        return walletService.getMyWallet(user.userId());
    }

    @PostMapping("/me/top-up")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Simulated top-up (recorded as a double-entry movement from the SYSTEM wallet)")
    public TopUpResponse topUp(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody TopUpRequest request) {
        return walletService.topUp(user.userId(), request.amount());
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "My transaction history (top-ups and transfers), newest first",
            description = "Optional 'from' / 'to' are inclusive dates in UTC, e.g. 2026-11-01")
    public PageResponse<TransactionHistoryEntry> transactions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return historyService.history(user.userId(), from, to, page, size);
    }
}
