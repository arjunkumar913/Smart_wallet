package com.vardhan.common.events;

/**
 * RabbitMQ names shared by all services, so publishers and consumers never drift apart.
 */
public final class EventTopology {

    /** Topic exchange every domain event is published to. */
    public static final String EXCHANGE = "wallet.events";

    /** Dead-letter exchange: messages that fail after retries end up here. */
    public static final String DEAD_LETTER_EXCHANGE = "wallet.events.dlx";

    public static final String USER_REGISTERED = "user.registered";
    public static final String TRANSFER_COMPLETED = "transfer.completed";

    private EventTopology() {
    }
}
