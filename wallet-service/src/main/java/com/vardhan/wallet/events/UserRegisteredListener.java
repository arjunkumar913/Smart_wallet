package com.vardhan.wallet.events;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.UserRegisteredEvent;
import com.vardhan.wallet.wallet.Wallet;
import com.vardhan.wallet.wallet.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a wallet for every newly registered user (event-driven: auth-service never calls us directly).
 * Idempotent: a redelivered event is detected via processed_events and ignored.
 */
@Component
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final WalletRepository walletRepository;
    private final ProcessedEventRepository processedEventRepository;

    public UserRegisteredListener(WalletRepository walletRepository,
                                  ProcessedEventRepository processedEventRepository) {
        this.walletRepository = walletRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = EventTopology.WALLET_USER_REGISTERED_QUEUE)
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Duplicate {} eventId={} ignored", EventTopology.USER_REGISTERED, event.eventId());
            return;
        }
        if (!walletRepository.existsByUserId(event.userId())) {
            walletRepository.save(Wallet.forUser(event.userId(), event.email()));
            log.info("Created wallet for userId={}", event.userId());
        }
        processedEventRepository.save(new ProcessedEvent(event.eventId(), EventTopology.USER_REGISTERED));
    }
}
