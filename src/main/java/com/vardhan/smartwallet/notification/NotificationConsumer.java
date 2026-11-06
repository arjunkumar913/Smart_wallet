package com.vardhan.smartwallet.notification;

/**
 * @RabbitListener on notifications.queue.
 */
public class NotificationConsumer {
    // TODO: Idempotent: skip if event_id already stored
    // TODO: Throw on failure so message routes to DLQ
}
