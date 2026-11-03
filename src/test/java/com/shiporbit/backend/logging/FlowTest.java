package com.shiporbit.backend.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlowTest {

    private static final Logger LOG = LoggerFactory.getLogger(FlowTest.class);

    @AfterEach
    void clear() {
        Flow.end();
    }

    @Test
    void numbersFollowExecutionOrderAndDepth() throws Exception {
        Flow.start("t-1");
        assertEquals("1", Flow.step(LOG, "received"));
        assertEquals("2", Flow.step(LOG, "validated"));
        try (Flow.Scope s = Flow.begin(LOG, "live partners")) {          // 3
            assertEquals("3", Flow.currentStep());
            assertEquals("3", MDC.get(Flow.MDC_STEP));
            Flow.Branch a = Flow.branch(LOG, "Delhivery");                  // 3a
            Flow.Branch b = Flow.branch(LOG, "ShreeMurti");                 // 3b
            assertEquals("3a", a.label());
            assertEquals("3b", b.label());
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<String> fb = ex.submit(() -> {
                    try (Flow.Scope sb = b.enter()) {
                        assertEquals("t-1", MDC.get(Flow.MDC_TRACE_ID));
                        return Flow.step(LOG, "serviceable");
                    }
                });
                Future<String> fa = ex.submit(() -> {
                    try (Flow.Scope sa = a.enter()) {
                        Flow.step(LOG, "serviceable");
                        try (Flow.Scope deeper = Flow.begin(LOG, "rate")) {   // 3a2
                            return Flow.step(LOG, "http call");               // 3a2a
                        }
                    }
                });
                assertEquals("3b1", fb.get());
                assertEquals("3a2a", fa.get());
            }
            assertEquals("3c", Flow.step(LOG, "done"));
        }
        assertEquals("4", Flow.step(LOG, "response"));
        assertNull(MDC.get(Flow.MDC_STEP));                                // back at top level
    }

    @Test
    void wrapCarriesScopeToOtherThread() throws Exception {
        Flow.start("t-2");
        try (Flow.Scope s = Flow.begin(LOG, "email")) {                    // 1
            Runnable r = Flow.wrap(() -> assertEquals("1a", Flow.step(LOG, "sent")));
            Thread t = Thread.ofVirtual().start(r);
            t.join();
        }
    }

    @Test
    void lettersRollOver() {
        assertEquals("a", StepLabels.letters(1));
        assertEquals("z", StepLabels.letters(26));
        assertEquals("aa", StepLabels.letters(27));
        assertEquals("3", StepLabels.segment(2, 3));
    }

    @Test
    void outsideAFlowStepsStillLog() {
        assertNull(Flow.step(LOG, "startup"));
        assertNull(Flow.traceId());
    }
}
