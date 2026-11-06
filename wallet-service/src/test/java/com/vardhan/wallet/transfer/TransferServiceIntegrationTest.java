package com.vardhan.wallet.transfer;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.web.BusinessException;
import com.vardhan.wallet.wallet.Wallet;
import com.vardhan.wallet.wallet.WalletRepository;
import com.vardhan.wallet.wallet.WalletService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs the real TransferService against a real PostgreSQL (Testcontainers), because
 * row locks, unique indexes and transaction isolation only behave like production on a real database.
 * RabbitMQ is replaced by a mock: these tests are about money correctness, not messaging.
 *
 * Requires Docker to be running.
 */
@SpringBootTest(properties = {  // default MOCK web environment: full security config, no real server
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false"
})
@Testcontainers
class TransferServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final AtomicLong USER_IDS = new AtomicLong(1_000);

    @MockBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    TransferService transferService;
    @Autowired
    WalletService walletService;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    JdbcTemplate jdbc;

    // ---------------------------------------------------------------- concurrency

    @Test
    void hundredParallelTransfersInBothDirections_noDeadlock_noLostUpdate() throws Exception {
        Wallet alice = newWallet("alice", "1000.00");
        Wallet bob = newWallet("bob", "1000.00");

        // 50 x alice->bob and 50 x bob->alice, all released at the same instant.
        // Without ordered locking, opposite-direction transfers would deadlock.
        List<Object> outcomes = runConcurrently(100, i -> i % 2 == 0
                ? () -> send(alice, bob, "10.00")
                : () -> send(bob, alice, "10.00"));

        assertThat(outcomes).allMatch(o -> o instanceof TransferResult);
        assertThat(balanceOf(alice)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("1000.00");
        // every wallet took part in all 100 transfers + its own top-up
        assertThat(ledgerRows(alice)).isEqualTo(101);
        assertThat(ledgerRows(bob)).isEqualTo(101);
        assertSystemBalancesToZero();
    }

    @Test
    void concurrentOverspend_neverGoesNegative_exactlyAffordableTransfersSucceed() throws Exception {
        Wallet alice = newWallet("alice", "100.00");
        Wallet bob = newWallet("bob", null);

        // 20 parallel attempts to send 10 from a balance of 100: only 10 can succeed.
        List<Object> outcomes = runConcurrently(20, i -> () -> send(alice, bob, "10.00"));

        assertThat(outcomes.stream().filter(o -> o instanceof TransferResult)).hasSize(10);
        assertThat(outcomes.stream().filter(o -> isBusinessError(o, "INSUFFICIENT_FUNDS"))).hasSize(10);
        assertThat(balanceOf(alice)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("100.00");
        assertSystemBalancesToZero();
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    void sameKeyTwice_movesMoneyOnce_andReturnsTheOriginalResponse() {
        Wallet alice = newWallet("alice", "500.00");
        Wallet bob = newWallet("bob", null);
        String key = UUID.randomUUID().toString();

        TransferResult first = transferService.transfer(alice.getUserId(), key, request(bob, "200.00"));
        TransferResult retry = transferService.transfer(alice.getUserId(), key, request(bob, "200.00"));

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.response().transactionId()).isEqualTo(first.response().transactionId());
        assertThat(balanceOf(alice)).isEqualByComparingTo("300.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("200.00");
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(EventTopology.EXCHANGE), eq(EventTopology.TRANSFER_COMPLETED), any(Object.class));
    }

    @Test
    void sameKeyWithDifferentAmount_isRejected() {
        Wallet alice = newWallet("alice", "500.00");
        Wallet bob = newWallet("bob", null);
        String key = UUID.randomUUID().toString();
        transferService.transfer(alice.getUserId(), key, request(bob, "200.00"));

        assertBusinessError(() -> transferService.transfer(alice.getUserId(), key, request(bob, "250.00")),
                "IDEMPOTENCY_KEY_REUSED");
        assertThat(balanceOf(alice)).isEqualByComparingTo("300.00");
    }

    @Test
    void sameKeySentConcurrently_debitsExactlyOnce() throws Exception {
        Wallet alice = newWallet("alice", "1000.00");
        Wallet bob = newWallet("bob", null);
        String key = UUID.randomUUID().toString();

        // a client retrying aggressively: 10 copies of the same request at once
        List<Object> outcomes = runConcurrently(10,
                i -> () -> transferService.transfer(alice.getUserId(), key, request(bob, "10.00")));

        long freshTransfers = outcomes.stream()
                .filter(o -> o instanceof TransferResult r && !r.replayed()).count();
        assertThat(freshTransfers).isEqualTo(1);
        assertThat(outcomes).allMatch(o -> o instanceof TransferResult
                || isBusinessError(o, "IDEMPOTENCY_KEY_IN_PROGRESS"));
        assertThat(balanceOf(alice)).isEqualByComparingTo("990.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("10.00");
    }

    // ---------------------------------------------------------------- rules

    @Test
    void transferRules_areEnforced_andFailuresWriteNothing() {
        Wallet alice = newWallet("alice", "100.00");
        Wallet bob = newWallet("bob", null);
        long rowsBefore = ledgerRows(alice);

        assertBusinessError(() -> send(alice, alice, "1.00"), "SELF_TRANSFER");
        assertBusinessError(() -> send(alice, bob, "100.01"), "INSUFFICIENT_FUNDS");
        assertBusinessError(() -> send(alice, bob, "100000.01"), "TRANSFER_LIMIT_EXCEEDED");
        assertBusinessError(() -> transferService.transfer(alice.getUserId(), UUID.randomUUID().toString(),
                new TransferRequest("nobody@test.dev", new BigDecimal("1.00"), null)), "RECIPIENT_NOT_FOUND");
        assertBusinessError(() -> transferService.transfer(alice.getUserId(), " ", request(bob, "1.00")),
                "MISSING_IDEMPOTENCY_KEY");

        Wallet frozenBob = walletRepository.findById(bob.getId()).orElseThrow();
        frozenBob.freeze();
        walletRepository.save(frozenBob);
        assertBusinessError(() -> send(alice, bob, "1.00"), "RECIPIENT_WALLET_FROZEN");

        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
        assertThat(ledgerRows(alice)).isEqualTo(rowsBefore);
    }

    @Test
    void fingerprint_treatsEquivalentAmountsAsTheSameRequest() {
        assertThat(TransferService.fingerprint("a@test.dev", new BigDecimal("500"), null))
                .isEqualTo(TransferService.fingerprint("a@test.dev", new BigDecimal("500.00"), null));
        assertThat(TransferService.fingerprint("a@test.dev", new BigDecimal("500"), null))
                .isNotEqualTo(TransferService.fingerprint("a@test.dev", new BigDecimal("500.01"), null));
    }

    // ---------------------------------------------------------------- helpers

    private Wallet newWallet(String name, String topUp) {
        long userId = USER_IDS.incrementAndGet();
        walletRepository.save(Wallet.forUser(userId, name + userId + "@test.dev"));
        if (topUp != null) {
            walletService.topUp(userId, new BigDecimal(topUp));
        }
        return walletRepository.findByUserId(userId).orElseThrow();
    }

    private TransferResult send(Wallet from, Wallet to, String amount) {
        return transferService.transfer(from.getUserId(), UUID.randomUUID().toString(), request(to, amount));
    }

    private static TransferRequest request(Wallet to, String amount) {
        return new TransferRequest(to.getEmail(), new BigDecimal(amount), null);
    }

    private BigDecimal balanceOf(Wallet wallet) {
        return walletRepository.findById(wallet.getId()).orElseThrow().getBalance();
    }

    private long ledgerRows(Wallet wallet) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries WHERE wallet_id = ?", Long.class, wallet.getId());
    }

    /** The double-entry invariant: money only moves, so all balances (SYSTEM included) sum to zero. */
    private void assertSystemBalancesToZero() {
        assertThat(jdbc.queryForObject("SELECT SUM(balance) FROM wallets", BigDecimal.class)).isEqualByComparingTo("0");
    }

    /** Starts all tasks at the same instant; returns each task's result or the exception it threw. */
    private static List<Object> runConcurrently(int count, IntFunction<Callable<?>> taskFactory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Callable<?> task = taskFactory.apply(i);
            futures.add(pool.submit(() -> {
                startSignal.await();
                return task.call();
            }));
        }
        startSignal.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("all transfers finish (no deadlock)").isTrue();

        List<Object> outcomes = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                outcomes.add(future.get());
            } catch (ExecutionException e) {
                outcomes.add(e.getCause());
            }
        }
        return outcomes;
    }

    private static boolean isBusinessError(Object outcome, String code) {
        return outcome instanceof BusinessException e && code.equals(e.getCode());
    }

    private static void assertBusinessError(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
