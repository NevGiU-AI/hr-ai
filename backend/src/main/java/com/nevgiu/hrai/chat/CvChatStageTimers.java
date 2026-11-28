package com.nevgiu.hrai.chat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** Timings for single-candidate chat. Only fixed application-owned values become metric labels. */
@Component
public class CvChatStageTimers {
    static final String METRIC = "hr.ai.cv.chat.stage";

    enum Stage {
        POLICY("policy"), RETRIEVAL("retrieval"), GENERATION("generation");

        private final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    private record Outcomes(Timer completed, Timer failed) {}

    private final MeterRegistry registry;
    private final Map<Stage, Outcomes> timers = new EnumMap<>(Stage.class);

    public CvChatStageTimers(MeterRegistry registry) {
        this.registry = registry;
        for (Stage stage : Stage.values()) {
            timers.put(stage, new Outcomes(register(stage, "completed"), register(stage, "failed")));
        }
    }

    <T> T measure(Stage stage, Supplier<T> operation) {
        Outcomes outcomes = timers.get(stage);
        Timer.Sample sample = Timer.start(registry);
        Timer timer = outcomes.failed();
        try {
            T result = operation.get();
            // A normal refusal or empty result is a completed operation, not a technical failure.
            timer = outcomes.completed();
            return result;
        } finally {
            sample.stop(timer);
        }
    }

    private Timer register(Stage stage, String outcome) {
        return Timer.builder(METRIC)
                .description("Single-candidate CV chat stage duration")
                .tags("stage", stage.label, "outcome", outcome)
                .register(registry);
    }
}
