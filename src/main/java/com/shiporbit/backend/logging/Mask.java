package com.shiporbit.backend.logging;

/**
 * Explicit masking for values you pass to a log statement on purpose.
 * LogMasker already scrubs every line; use these when the value has no recognisable shape
 * (e.g. a raw API key variable) or to make intent obvious in code.
 */
public final class Mask {

    private Mask() {
    }

    /** Never shows any part of the value: "****" (or "<none>" when empty). */
    public static String secret(String value) {
        return value == null || value.isEmpty() ? "<none>" : "****";
    }

    /** Shows the first 4 characters only, e.g. for telling two tokens apart: "a60d****". */
    public static String hint(String value) {
        if (value == null || value.isEmpty()) {
            return "<none>";
        }
        return value.length() <= 4 ? "****" : value.substring(0, 4) + "****";
    }

    /** saurabh@gmail.com -> sa***@gmail.com */
    public static String email(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    /** 9876543210 -> 98******10 */
    public static String phone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "****";
        }
        return digits.substring(0, 2) + "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 2);
    }
}
