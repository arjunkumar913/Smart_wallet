package com.vardhan.wallet.topup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.TopUpCompletedEvent;
import com.vardhan.common.security.JwtService;
import com.vardhan.wallet.IntegrationTestBase;
import com.vardhan.wallet.payment.MockGateway;
import com.vardhan.wallet.wallet.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Top-ups end to end inside wallet-service, against a real PostgreSQL: HTTP, signature checks,
 * row locks and the ledger. The gateway is the built-in demo bank, which signs its webhooks the
 * same way Razorpay does, so the code under test is the code that runs in production.
 */
class TopUpIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockGateway bank;
    @Autowired
    PaymentWebhookService webhookService;
    @Autowired
    TopUpReconciler reconciler;

    // ---------------------------------------------------------------- happy path

    @Test
    void paidTopUp_creditsTheWallet_withABalancedLedgerPair_andAnnouncesIt() throws Exception {
        Wallet alice = newWallet("alice", null);
        clearInvocations(rabbitTemplate);

        JsonNode topUp = json(createTopUp(alice, "500.00", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("MOCK"))
                .andExpect(jsonPath("$.checkout.amountInPaise").value(50_000)));
        String orderId = topUp.at("/checkout/orderId").asText();
        assertThat(orderId).startsWith("order_mock_");
        // creating the order moves no money
        assertThat(balanceOf(alice)).isEqualByComparingTo("0.00");

        deliverWebhook(captured(orderId, "pay_1", 50_000)).andExpect(status().isOk());

        assertThat(balanceOf(alice)).isEqualByComparingTo("500.00");
        JsonNode done = json(mvc.perform(as(alice, get("/api/topups/{id}", topUp.get("topUpId").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentMethod").value("upi"))
                .andExpect(jsonPath("$.checkout").doesNotExist()));

        // one DEBIT on the SYSTEM wallet + one CREDIT on Alice's wallet, sharing the transaction id
        String transactionId = done.get("transactionId").asText();
        assertThat(jdbc.queryForList("SELECT entry_type FROM ledger_entries WHERE transaction_id = ?::uuid ORDER BY id",
                String.class, transactionId)).containsExactly("DEBIT", "CREDIT");
        assertThat(jdbc.queryForObject("SELECT description FROM ledger_entries WHERE transaction_id = ?::uuid AND wallet_id = ?",
                String.class, transactionId, alice.getId())).isEqualTo("Added money via Demo Bank (UPI)");
        assertSystemBalancesToZero();
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(EventTopology.EXCHANGE), eq(EventTopology.TOP_UP_COMPLETED), any(TopUpCompletedEvent.class));
    }

    // ---------------------------------------------------------------- exactly-once crediting

    @Test
    void sameWebhookDeliveredTenTimesAtOnce_creditsExactlyOnce() throws Exception {
        Wallet alice = newWallet("alice", null);
        String orderId = orderIdOf(createTopUp(alice, "200.00", UUID.randomUUID().toString()));
        byte[] webhook = captured(orderId, "pay_dup", 20_000);
        String signature = bank.signWebhook(webhook);
        clearInvocations(rabbitTemplate);

        // a gateway retrying aggressively: ten copies of the same event released at the same instant
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> deliveries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            deliveries.add(pool.submit(() -> {
                start.await();
                webhookService.handle(webhook, signature);
                return null;
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        for (Future<?> delivery : deliveries) {
            delivery.get();     // every delivery is answered without an error
        }

        assertThat(balanceOf(alice)).isEqualByComparingTo("200.00");
        assertThat(ledgerRows(alice)).isEqualTo(1);
        assertSystemBalancesToZero();
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(EventTopology.EXCHANGE), eq(EventTopology.TOP_UP_COMPLETED), any(TopUpCompletedEvent.class));
    }

    @Test
    void webhook_browserConfirmation_andReconciliation_togetherStillCreditOnce() throws Exception {
        Wallet alice = newWallet("alice", null);
        JsonNode topUp = json(createTopUp(alice, "300.00", UUID.randomUUID().toString()));
        String topUpId = topUp.get("topUpId").asText();
        String orderId = topUp.at("/checkout/orderId").asText();

        // 1. the browser gets there first, with the gateway's signature
        confirm(alice, topUpId, "pay_3", bank.signPayment(orderId, "pay_3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        // 2. then the webhook for the same payment arrives, 3. then somebody asks the gateway again
        deliverWebhook(captured(orderId, "pay_3", 30_000)).andExpect(status().isOk());
        mvc.perform(as(alice, post("/api/topups/{id}/sync", topUpId))).andExpect(status().isOk());
        reconciler.reconcileOne(UUID.fromString(topUpId));

        assertThat(balanceOf(alice)).isEqualByComparingTo("300.00");
        assertThat(ledgerRows(alice)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- signatures

    @Test
    void webhookWithAWrongOrMissingSignature_isRejected_andNothingIsCredited() throws Exception {
        Wallet alice = newWallet("alice", null);
        String orderId = orderIdOf(createTopUp(alice, "100.00", UUID.randomUUID().toString()));
        byte[] webhook = captured(orderId, "pay_forged", 10_000);

        mvc.perform(post("/api/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(webhook)
                        .header("X-Razorpay-Signature", "0".repeat(64)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
        mvc.perform(post("/api/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(webhook))
                .andExpect(status().isUnauthorized());
        // a valid signature for a DIFFERENT body does not help either
        mvc.perform(post("/api/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(webhook)
                        .header("X-Razorpay-Signature", bank.signWebhook(captured(orderId, "pay_forged", 1))))
                .andExpect(status().isUnauthorized());

        assertThat(balanceOf(alice)).isEqualByComparingTo("0.00");
    }

    @Test
    void browserConfirmation_withAForgedSignature_isRejected() throws Exception {
        Wallet alice = newWallet("alice", null);
        JsonNode topUp = json(createTopUp(alice, "100.00", UUID.randomUUID().toString()));

        confirm(alice, topUp.get("topUpId").asText(), "pay_fake", "a".repeat(64))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_SIGNATURE"));

        assertThat(balanceOf(alice)).isEqualByComparingTo("0.00");
    }

    @Test
    void paymentForADifferentAmountThanTheOrder_isNotCredited() throws Exception {
        Wallet alice = newWallet("alice", null);
        String orderId = orderIdOf(createTopUp(alice, "100.00", UUID.randomUUID().toString()));

        deliverWebhook(captured(orderId, "pay_short", 100)).andExpect(status().isOk());   // 1 rupee, not 100

        assertThat(balanceOf(alice)).isEqualByComparingTo("0.00");
    }

    // ---------------------------------------------------------------- idempotent creation

    @Test
    void sameIdempotencyKey_returnsTheSameTopUp_andADifferentAmountIsRejected() throws Exception {
        Wallet alice = newWallet("alice", null);
        String key = UUID.randomUUID().toString();

        JsonNode first = json(createTopUp(alice, "250.00", key).andExpect(status().isCreated()));
        JsonNode retry = json(createTopUp(alice, "250.00", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")));

        assertThat(retry.get("topUpId")).isEqualTo(first.get("topUpId"));
        assertThat(retry.at("/checkout/orderId")).isEqualTo(first.at("/checkout/orderId"));   // no second gateway order
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM top_ups WHERE user_id = ?", Long.class, alice.getUserId()))
                .isEqualTo(1);

        createTopUp(alice, "999.00", key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(as(alice, post("/api/topups")).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    // ---------------------------------------------------------------- failures and recovery

    @Test
    void failedPayment_marksTheTopUpFailed_butALaterSuccessOnTheSameOrderStillCredits() throws Exception {
        Wallet alice = newWallet("alice", null);
        JsonNode topUp = json(createTopUp(alice, "150.00", UUID.randomUUID().toString()));
        String topUpId = topUp.get("topUpId").asText();
        String orderId = topUp.at("/checkout/orderId").asText();

        deliverWebhook(failed(orderId, "pay_declined", "Insufficient funds in the bank account")).andExpect(status().isOk());
        mvc.perform(as(alice, get("/api/topups/{id}", topUpId)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Insufficient funds in the bank account"));
        assertThat(balanceOf(alice)).isEqualByComparingTo("0.00");

        // the customer retries on the same order and this time the bank takes the money
        deliverWebhook(captured(orderId, "pay_retry", 15_000)).andExpect(status().isOk());
        mvc.perform(as(alice, get("/api/topups/{id}", topUpId)))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
        assertThat(balanceOf(alice)).isEqualByComparingTo("150.00");

        // a failure reported late never undoes a success
        deliverWebhook(failed(orderId, "pay_declined", "late duplicate")).andExpect(status().isOk());
        mvc.perform(as(alice, get("/api/topups/{id}", topUpId))).andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void lostWebhook_isRecoveredByAskingTheGateway() throws Exception {
        Wallet alice = newWallet("alice", null);
        String topUpId = json(createTopUp(alice, "400.00", UUID.randomUUID().toString())).get("topUpId").asText();

        // the bank takes the money, but its webhook never reaches us
        mockPay(alice, topUpId, "SUCCESS_WEBHOOK_LOST").andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(balanceOf(alice)).isEqualByComparingTo("0.00");

        mvc.perform(as(alice, post("/api/topups/{id}/sync", topUpId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        assertThat(balanceOf(alice)).isEqualByComparingTo("400.00");
    }

    @Test
    void reconciliationJob_creditsPaidTopUps_andExpiresOnesNobodyPaid() throws Exception {
        Wallet alice = newWallet("alice", null);
        String paid = json(createTopUp(alice, "120.00", UUID.randomUUID().toString())).get("topUpId").asText();
        String abandoned = json(createTopUp(alice, "80.00", UUID.randomUUID().toString())).get("topUpId").asText();
        String fresh = json(createTopUp(alice, "60.00", UUID.randomUUID().toString())).get("topUpId").asText();
        mockPay(alice, paid, "SUCCESS_WEBHOOK_LOST");
        // make the first two look old: one past the grace period, one past the payment time limit
        jdbc.update("UPDATE top_ups SET created_at = now() - interval '5 minutes' WHERE id = ?::uuid", paid);
        jdbc.update("UPDATE top_ups SET created_at = now() - interval '31 minutes' WHERE id = ?::uuid", abandoned);

        reconciler.reconcile();

        mvc.perform(as(alice, get("/api/topups/{id}", paid))).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(as(alice, get("/api/topups/{id}", abandoned))).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(as(alice, get("/api/topups/{id}", fresh))).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(balanceOf(alice)).isEqualByComparingTo("120.00");
    }

    // ---------------------------------------------------------------- rules and access

    @Test
    void topUps_arePrivate_limited_andNotStartedForFrozenWallets() throws Exception {
        Wallet alice = newWallet("alice", null);
        Wallet bob = newWallet("bob", null);
        String aliceTopUp = json(createTopUp(alice, "100.00", UUID.randomUUID().toString())).get("topUpId").asText();

        // another user's top-up is simply "not found", for reading and for paying
        mvc.perform(as(bob, get("/api/topups/{id}", aliceTopUp)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOP_UP_NOT_FOUND"));
        mockPay(bob, aliceTopUp, "SUCCESS").andExpect(status().isNotFound());
        mvc.perform(post("/api/topups").header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10}"))
                .andExpect(status().isUnauthorized());

        createTopUp(alice, "50000.01", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOP_UP_LIMIT_EXCEEDED"));
        createTopUp(alice, "0.50", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        freeze(bob);
        createTopUp(bob, "100.00", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));
    }

    @Test
    void moneyAlreadyCollected_isCreditedEvenIfTheWalletWasFrozenMeanwhile() throws Exception {
        Wallet alice = newWallet("alice", null);
        String orderId = orderIdOf(createTopUp(alice, "75.00", UUID.randomUUID().toString()));
        freeze(alice);

        deliverWebhook(captured(orderId, "pay_frozen", 7_500)).andExpect(status().isOk());

        assertThat(balanceOf(alice)).isEqualByComparingTo("75.00");
    }

    @Test
    void demoBank_approveCredits_declineFails_andAFinishedTopUpCannotBePaidAgain() throws Exception {
        Wallet alice = newWallet("alice", null);
        String approved = json(createTopUp(alice, "500.00", UUID.randomUUID().toString())).get("topUpId").asText();
        String declined = json(createTopUp(alice, "500.00", UUID.randomUUID().toString())).get("topUpId").asText();

        mockPay(alice, approved, "SUCCESS").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mockPay(alice, declined, "FAILURE").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Payment was declined by the bank"));
        mockPay(alice, approved, "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOP_UP_NOT_PAYABLE"));

        assertThat(balanceOf(alice)).isEqualByComparingTo("500.00");
        verify(rabbitTemplate, never()).convertAndSend(eq(EventTopology.EXCHANGE), eq("topup.failed"), any(Object.class));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions createTopUp(Wallet wallet, String amount, String idempotencyKey) throws Exception {
        return mvc.perform(as(wallet, post("/api/topups"))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    private ResultActions confirm(Wallet wallet, String topUpId, String paymentId, String signature) throws Exception {
        return mvc.perform(as(wallet, post("/api/topups/{id}/confirm", topUpId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":\"" + paymentId + "\",\"signature\":\"" + signature + "\"}"));
    }

    private ResultActions mockPay(Wallet wallet, String topUpId, String outcome) throws Exception {
        return mvc.perform(as(wallet, post("/api/topups/{id}/mock-pay", topUpId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + outcome + "\"}"));
    }

    /** Sends a webhook the way the gateway would: raw body plus its HMAC signature, no JWT. */
    private ResultActions deliverWebhook(byte[] body) throws Exception {
        return mvc.perform(post("/api/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Razorpay-Signature", bank.signWebhook(body)));
    }

    /** A payment.captured event in Razorpay's webhook format. */
    private static byte[] captured(String orderId, String paymentId, long paise) {
        return ("{\"entity\":\"event\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + paymentId + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + paise
                + ",\"currency\":\"INR\",\"status\":\"captured\",\"method\":\"upi\"}}}}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] failed(String orderId, String paymentId, String reason) {
        return ("{\"entity\":\"event\",\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + paymentId + "\",\"order_id\":\"" + orderId + "\",\"status\":\"failed\","
                + "\"error_description\":\"" + reason + "\"}}}}").getBytes(StandardCharsets.UTF_8);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String orderIdOf(ResultActions created) throws Exception {
        return json(created.andExpect(status().isCreated())).at("/checkout/orderId").asText();
    }

    private void freeze(Wallet wallet) {
        Wallet current = walletRepository.findById(wallet.getId()).orElseThrow();
        current.freeze();
        walletRepository.save(current);
    }

    private long ledgerRows(Wallet wallet) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries WHERE wallet_id = ?", Long.class, wallet.getId());
    }

    /** The double-entry invariant: money only moves, so all balances (SYSTEM included) sum to zero. */
    private void assertSystemBalancesToZero() {
        assertThat(jdbc.queryForObject("SELECT SUM(balance) FROM wallets", BigDecimal.class)).isEqualByComparingTo("0");
    }

    private MockHttpServletRequestBuilder as(Wallet wallet, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(wallet.getUserId(), wallet.getEmail(), "USER"));
    }
}
