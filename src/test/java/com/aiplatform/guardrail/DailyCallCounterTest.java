package com.aiplatform.guardrail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyCallCounterTest {

    @Test
    void recordAndGetCount_incrementsPerKey() {
        DailyCallCounter counter = new DailyCallCounter();
        assertEquals(1, counter.recordAndGetCount("192.168.1.1", "/api/jobs/score/guest"));
        assertEquals(2, counter.recordAndGetCount("192.168.1.1", "/api/jobs/score/guest"));
        assertEquals(3, counter.recordAndGetCount("192.168.1.1", "/api/jobs/score/guest"));
    }

    @Test
    void recordAndGetCount_separateKeysAreIndependent() {
        DailyCallCounter counter = new DailyCallCounter();
        assertEquals(1, counter.recordAndGetCount("10.0.0.1", "/api/jobs/score/guest"));
        assertEquals(1, counter.recordAndGetCount("10.0.0.2", "/api/jobs/score/guest"));
        assertEquals(1, counter.recordAndGetCount("10.0.0.1", "/api/resume/tailor/guest"));
    }

    @Test
    void getCount_withoutRecording() {
        DailyCallCounter counter = new DailyCallCounter();
        assertEquals(0, counter.getCount("1.2.3.4", "/api/jobs/score/guest"));
        counter.recordAndGetCount("1.2.3.4", "/api/jobs/score/guest");
        assertEquals(1, counter.getCount("1.2.3.4", "/api/jobs/score/guest"));
    }
}
