package com.shiporbit.backend.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Numbered, per-request flow logging. Every log line carries MDC trace_id and step.
 * <p>
 * Step numbers are assigned at runtime in the order steps happen - code never contains a number, so
 * adding, removing or reordering a step renumbers the rest automatically.
 * Depth decides the format: top level 1, 2, 3 -> inside a scope 3a, 3b -> deeper 3a1, 3a2 -> 3a1a ...
 * <pre>
 *   Flow.step(LOGGER, "Request validated: {} -> {}", from, to);         // leaf step: "2"
 *   try (Flow.Scope s = Flow.begin(LOGGER, "Live partners")) {           // scope step: "3"
 *       Flow.step(LOGGER, "Delhivery priced");                           //   child:    "3a"
 *   }
 *   // parallel work: reserve the number in the caller thread (stable order), enter it in the worker
 *   Flow.Branch b = Flow.branch(LOGGER, "Delhivery");                    // "3a"
 *   executor.submit(() -> { try (Flow.Scope s = b.enter()) { Flow.step(LOGGER, "rate fetched"); } }); // "3a1"
 * </pre>
 * Ordinary LOGGER calls inside a scope carry the scope's step in MDC, so they line up with the flow.
 * Outside a request (startup, schedulers) steps log without a number unless Flow.start() was called.
 */
public final class Flow {

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_STEP = "step";

    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private Flow() {
    }

    // ---------------------------------------------------------------- lifecycle

    /** Starts a flow on this thread (TraceFilter does this per HTTP request). */
    public static void start(String traceId) {
        Frame root = new Frame(traceId, null, "", -1);
        CURRENT.set(root);
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.remove(MDC_STEP);
    }

    /** Ends the flow on this thread and clears MDC. */
    public static void end() {
        CURRENT.remove();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_STEP);
    }

    /** Current trace id, or null outside a flow. */
    public static String traceId() {
        Frame f = CURRENT.get();
        return f == null ? null : f.traceId;
    }

    /** Label of the current scope ("" at top level), or null outside a flow. */
    public static String currentStep() {
        Frame f = CURRENT.get();
        return f == null ? null : f.label;
    }

    // ---------------------------------------------------------------- steps

    /** Logs a numbered leaf step at INFO. Returns its label (e.g. "3b"), or null outside a flow. */
    public static String step(Logger log, String format, Object... args) {
        return step(log, Level.INFO, format, args);
    }

    public static String stepWarn(Logger log, String format, Object... args) {
        return step(log, Level.WARN, format, args);
    }

    public static String stepError(Logger log, String format, Object... args) {
        return step(log, Level.ERROR, format, args);
    }

    public static String stepDebug(Logger log, String format, Object... args) {
        return step(log, Level.DEBUG, format, args);
    }

    public static String step(Logger log, Level level, String format, Object... args) {
        Frame parent = CURRENT.get();
        if (parent == null) {
            emit(log, level, format, args);
            return null;
        }
        String label = parent.nextChildLabel();
        String scopeLabel = MDC.get(MDC_STEP);
        MDC.put(MDC_STEP, label);
        try {
            emit(log, level, format, args);
        } finally {
            restoreStep(scopeLabel);
        }
        return label;
    }

    /** Like SLF4J's classic API: a Throwable as the last argument is logged as the cause (stack trace). */
    private static void emit(Logger log, Level level, String format, Object[] args) {
        if (!log.isEnabledForLevel(level)) {
            return;
        }
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable t) {
            Object[] rest = java.util.Arrays.copyOf(args, args.length - 1);
            log.atLevel(level).setCause(t).log(format, rest);
        } else {
            log.atLevel(level).log(format, args);
        }
    }

    /** Logs a numbered step at INFO and makes it the parent of the steps that follow until close(). */
    public static Scope begin(Logger log, String format, Object... args) {
        return branch(log, format, args).enter();
    }

    /**
     * Reserves and logs a numbered step without entering it - for work that continues on another thread.
     * Call in the submitting thread so numbers follow submission order; call enter() in the worker.
     */
    public static Branch branch(Logger log, String format, Object... args) {
        Frame parent = CURRENT.get();
        if (parent == null) {
            emit(log, Level.INFO, format, args);
            return new Branch(null);
        }
        Frame child = new Frame(parent.traceId, parent, parent.nextChildLabel(), parent.depth + 1);
        String scopeLabel = MDC.get(MDC_STEP);
        MDC.put(MDC_STEP, child.label);
        try {
            emit(log, Level.INFO, format, args);
        } finally {
            restoreStep(scopeLabel);
        }
        return new Branch(child);
    }

    // ---------------------------------------------------------------- thread hand-off

    /** Runs r on another thread inside the caller's current scope (used as executor TaskDecorator). */
    public static Runnable wrap(Runnable r) {
        Frame captured = CURRENT.get();
        if (captured == null) {
            return r;
        }
        return () -> {
            try (Scope ignored = enter(captured)) {
                r.run();
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> c) {
        Frame captured = CURRENT.get();
        if (captured == null) {
            return c;
        }
        return () -> {
            try (Scope ignored = enter(captured)) {
                return c.call();
            }
        };
    }

    private static Scope enter(Frame frame) {
        Frame previousFrame = CURRENT.get();
        String previousTrace = MDC.get(MDC_TRACE_ID);
        String previousStep = MDC.get(MDC_STEP);
        CURRENT.set(frame);
        MDC.put(MDC_TRACE_ID, frame.traceId);
        restoreStep(frame.label);
        return new Scope(previousFrame, previousTrace, previousStep);
    }

    private static void restoreStep(String label) {
        if (label == null || label.isEmpty()) {
            MDC.remove(MDC_STEP);
        } else {
            MDC.put(MDC_STEP, label);
        }
    }

    // ---------------------------------------------------------------- types

    /** Closes a scope: restores the previous scope (or clears MDC in a worker thread). */
    public static final class Scope implements AutoCloseable {
        private final Frame previousFrame;
        private final String previousTrace;
        private final String previousStep;
        private boolean closed;

        private Scope(Frame previousFrame, String previousTrace, String previousStep) {
            this.previousFrame = previousFrame;
            this.previousTrace = previousTrace;
            this.previousStep = previousStep;
        }

        static Scope noop() {
            Scope s = new Scope(CURRENT.get(), MDC.get(MDC_TRACE_ID), MDC.get(MDC_STEP));
            s.closed = true;
            return s;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previousFrame == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previousFrame);
            }
            if (previousTrace == null) {
                MDC.remove(MDC_TRACE_ID);
            } else {
                MDC.put(MDC_TRACE_ID, previousTrace);
            }
            restoreStep(previousStep);
        }
    }

    /** A reserved step; enter() it on the thread that does the work. */
    public static final class Branch {
        private final Frame frame;

        private Branch(Frame frame) {
            this.frame = frame;
        }

        public Scope enter() {
            return frame == null ? Scope.noop() : Flow.enter(frame);
        }

        public String label() {
            return frame == null ? null : frame.label;
        }
    }

    private static final class Frame {
        final String traceId;
        final Frame parent;
        final String label;
        final int depth;           // -1 = root (request), 0 = "1", 1 = "1a", 2 = "1a1", ...
        final AtomicInteger children = new AtomicInteger();

        Frame(String traceId, Frame parent, String label, int depth) {
            this.traceId = traceId;
            this.parent = parent;
            this.label = label;
            this.depth = depth;
        }

        String nextChildLabel() {
            return label + StepLabels.segment(depth + 1, children.incrementAndGet());
        }
    }
}
