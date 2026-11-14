package com.vardhan.notification.email;

import org.springframework.scheduling.annotation.Scheduled;

/** Runs the dispatcher on a timer. Registered by {@link EmailConfig} unless app.email.dispatch.enabled=false. */
public class EmailDispatchScheduler {

    private final EmailDispatcher dispatcher;

    public EmailDispatchScheduler(EmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.email.dispatch.interval-ms:5000}", initialDelayString = "${app.email.dispatch.interval-ms:5000}")
    public void run() {
        dispatcher.dispatchDue();
    }
}
