package com.shiporbit.backend.logging;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Stack traces with LogMasker applied (exception messages can carry partner responses / tokens).
 * Registered as %maskedEx and used at the end of logging.pattern.console / logging.pattern.file.
 */
public class MaskingThrowableConverter extends ExtendedThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.mask(super.convert(event));
    }
}
