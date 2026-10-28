package com.nevgiu.hrai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables application-wide scheduled infrastructure independently of any single business feature.
 * Current consumers include security-audit cleanup, original-CV retention, and semantic-index reconciliation. Keeping
 * this concern in a dedicated configuration also provides one place for a future scheduler pool, shutdown policy,
 * error handling, metrics, or tracing.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
