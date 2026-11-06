package com.vardhan.wallet;

import com.vardhan.wallet.wallet.Wallet;
import com.vardhan.wallet.wallet.WalletRepository;
import com.vardhan.wallet.wallet.WalletService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared setup for integration tests: ONE real PostgreSQL container (started once for the whole run)
 * and ONE Spring context, reused by every subclass. RabbitMQ is mocked; these tests are about
 * money correctness and the HTTP contract, not messaging. Requires Docker.
 */
@SpringBootTest(properties = {  // default MOCK web environment: full security config, no real server
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    /** Every test creates fresh users, so tests never interfere even though they share one database. */
    private static final AtomicLong USER_IDS = new AtomicLong(1_000);

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected WalletService walletService;
    @Autowired
    protected WalletRepository walletRepository;
    @Autowired
    protected JdbcTemplate jdbc;

    protected Wallet newWallet(String name, String topUp) {
        long userId = USER_IDS.incrementAndGet();
        walletRepository.save(Wallet.forUser(userId, name + userId + "@test.dev"));
        if (topUp != null) {
            walletService.topUp(userId, new BigDecimal(topUp));
        }
        return walletRepository.findByUserId(userId).orElseThrow();
    }

    protected BigDecimal balanceOf(Wallet wallet) {
        return walletRepository.findById(wallet.getId()).orElseThrow().getBalance();
    }
}
