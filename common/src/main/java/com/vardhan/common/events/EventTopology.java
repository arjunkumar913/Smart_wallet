package com.vardhan.common.events;

/**
 * Every RabbitMQ name in the system, in one place, so publishers and consumers never drift apart.
 */
public final class EventTopology {

    /** Topic exchange every domain event is published to. */
    public static final String EXCHANGE = "wallet.events";

    /** Dead-letter exchange: messages that still fail after retries end up here. */
    public static final String DEAD_LETTER_EXCHANGE = "wallet.events.dlx";

    // routing keys
    public static final String USER_REGISTERED = "user.registered";
    public static final String TRANSFER_COMPLETED = "transfer.completed";

    // queues (named <consumer>.<event>.queue) and their dead-letter queues
    public static final String WALLET_USER_REGISTERED_QUEUE = "wallet.user-registered.queue";
    public static final String WALLET_USER_REGISTERED_DLQ = "wallet.user-registered.dlq";
    public static final String NOTIFICATION_TRANSFER_COMPLETED_QUEUE = "notification.transfer-completed.queue";
    public static final String NOTIFICATION_TRANSFER_COMPLETED_DLQ = "notification.transfer-completed.dlq";

    private EventTopology() {
    }
}
