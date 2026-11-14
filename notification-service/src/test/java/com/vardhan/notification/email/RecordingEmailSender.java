package com.vardhan.notification.email;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Test double for a provider: records every accepted email and can be told to fail the next sends. */
public class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> accepted = new CopyOnWriteArrayList<>();
    private final Queue<EmailSendException> plannedFailures = new ConcurrentLinkedQueue<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile EmailSendException failAlways;
    private volatile long delayMillis;

    @Override
    public String send(EmailMessage message) {
        calls.incrementAndGet();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        EmailSendException failure = plannedFailures.poll();
        if (failure == null) {
            failure = failAlways;
        }
        if (failure != null) {
            throw failure;
        }
        accepted.add(message);
        return "test-" + accepted.size();
    }

    public void failNext(EmailSendException failure) {
        plannedFailures.add(failure);
    }

    public void failAlways(EmailSendException failure) {
        this.failAlways = failure;
    }

    public void delay(long millis) {
        this.delayMillis = millis;
    }

    public List<EmailMessage> accepted() {
        return accepted;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        accepted.clear();
        plannedFailures.clear();
        calls.set(0);
        failAlways = null;
        delayMillis = 0;
    }
}
