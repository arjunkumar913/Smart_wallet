package com.vardhan.wallet.web;

import com.vardhan.common.security.AuthenticatedUser;
import com.vardhan.wallet.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
@Tag(name = "Wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
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
}
