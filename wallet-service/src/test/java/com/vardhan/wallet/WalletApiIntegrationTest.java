package com.vardhan.wallet;

import com.vardhan.common.security.JwtService;
import com.vardhan.wallet.wallet.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the HTTP layer end to end inside wallet-service: JWT security, role checks,
 * request validation, and the history and admin endpoints, against the real database.
 */
class WalletApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    // ---------------------------------------------------------------- history

    @Test
    void history_isNewestFirst_paginated_andDateFiltered() throws Exception {
        Wallet alice = newWallet("alice", "100.00");
        Wallet bob = newWallet("bob", null);
        transfer(alice, bob, "10.00").andExpect(status().isCreated());
        transfer(alice, bob, "20.00").andExpect(status().isCreated());

        mvc.perform(as(alice, get("/api/wallets/me/transactions").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))   // 1 top-up + 2 transfers
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].amount").value(20.00))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(70.00))
                .andExpect(jsonPath("$.content[1].amount").value(10.00));

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        mvc.perform(as(alice, get("/api/wallets/me/transactions").param("from", today).param("to", today)))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(as(alice, get("/api/wallets/me/transactions").param("from", tomorrow)))
                .andExpect(jsonPath("$.totalElements").value(0));

        // the recipient sees the same transfers as CREDITs
        mvc.perform(as(bob, get("/api/wallets/me/transactions")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"));
    }

    @Test
    void history_rejectsInvalidParameters() throws Exception {
        Wallet alice = newWallet("alice", null);

        mvc.perform(as(alice, get("/api/wallets/me/transactions").param("from", "2026-11-10").param("to", "2026-11-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mvc.perform(as(alice, get("/api/wallets/me/transactions").param("size", "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(as(alice, get("/api/wallets/me/transactions").param("from", "not-a-date")))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- security

    @Test
    void endpoints_requireAToken_andAdminEndpointsRequireAdminRole() throws Exception {
        Wallet alice = newWallet("alice", null);

        mvc.perform(get("/api/wallets/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/wallets/me").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(alice, get("/api/admin/wallets")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(asAdmin(get("/api/admin/wallets")))
                .andExpect(status().isOk());
    }

    @Test
    void transfer_withoutIdempotencyKey_isRejected() throws Exception {
        Wallet alice = newWallet("alice", "50.00");
        Wallet bob = newWallet("bob", null);

        mvc.perform(as(alice, post("/api/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(bob, "5.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    // ---------------------------------------------------------------- admin

    @Test
    void frozenWallet_cannotSend_untilUnfrozen() throws Exception {
        Wallet alice = newWallet("alice", "100.00");
        Wallet bob = newWallet("bob", null);

        mvc.perform(asAdmin(post("/api/admin/wallets/{id}/freeze", alice.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        transfer(alice, bob, "10.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));

        mvc.perform(asAdmin(post("/api/admin/wallets/{id}/unfreeze", alice.getId())))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        transfer(alice, bob, "10.00").andExpect(status().isCreated());
        assertThat(balanceOf(alice)).isEqualByComparingTo("90.00");
    }

    @Test
    void systemWallet_cannotBeFrozen_andUnknownWalletIsNotFound() throws Exception {
        Long systemWalletId = jdbc.queryForObject("SELECT id FROM wallets WHERE wallet_type = 'SYSTEM'", Long.class);

        mvc.perform(asAdmin(post("/api/admin/wallets/{id}/freeze", systemWalletId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SYSTEM_WALLET_PROTECTED"));
        mvc.perform(asAdmin(post("/api/admin/wallets/{id}/freeze", 999_999)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.ResultActions transfer(Wallet from, Wallet to, String amount) throws Exception {
        return mvc.perform(as(from, post("/api/transfers"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody(to, amount)));
    }

    private static String transferBody(Wallet to, String amount) {
        return "{\"toEmail\":\"" + to.getEmail() + "\",\"amount\":" + amount + "}";
    }

    private MockHttpServletRequestBuilder as(Wallet wallet, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(wallet.getUserId(), wallet.getEmail(), "USER"));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(1L, "admin@test.dev", "ADMIN"));
    }
}
