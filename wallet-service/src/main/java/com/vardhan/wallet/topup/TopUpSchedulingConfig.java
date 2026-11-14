package com.vardhan.wallet.topup;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled for the reconciliation job. */
@Configuration
@EnableScheduling
public class TopUpSchedulingConfig {
}
