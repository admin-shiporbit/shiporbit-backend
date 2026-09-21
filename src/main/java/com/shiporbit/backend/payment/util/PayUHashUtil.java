package com.shiporbit.backend.payment.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PayU's hash formulas (confirmed against https://docs.payu.in/docs/generate-hash-payu-hosted,
 * September 2026). The request hash is forward order; PayU's callback hash is the SAME fields
 * in reverse order with SALT and key swapped ends. Getting either literal pipe count wrong
 * silently breaks every payment, so these two strings are built to match the docs character
 * for character rather than being assembled from a loop.
 */
public final class PayUHashUtil {

    private PayUHashUtil() {
    }

    /** sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT) */
    public static String requestHash(
            String key, String txnid, String amount, String productInfo,
            String firstName, String email,
            String udf1, String udf2, String udf3, String udf4, String udf5,
            String salt
    ) {
        String hashString = key + "|" + txnid + "|" + amount + "|" + productInfo + "|" + firstName + "|" + email
                + "|" + nullToEmpty(udf1) + "|" + nullToEmpty(udf2) + "|" + nullToEmpty(udf3)
                + "|" + nullToEmpty(udf4) + "|" + nullToEmpty(udf5)
                + "||||||" + salt;
        return sha512Hex(hashString);
    }

    /** sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key) */
    public static String responseHash(
            String salt, String status,
            String udf1, String udf2, String udf3, String udf4, String udf5,
            String email, String firstName, String productInfo, String amount, String txnid, String key
    ) {
        String hashString = salt + "|" + status + "||||||"
                + nullToEmpty(udf5) + "|" + nullToEmpty(udf4) + "|" + nullToEmpty(udf3)
                + "|" + nullToEmpty(udf2) + "|" + nullToEmpty(udf1)
                + "|" + email + "|" + firstName + "|" + productInfo + "|" + amount + "|" + txnid + "|" + key;
        return sha512Hex(hashString);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha512Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-512 is a mandatory JDK algorithm; this can only happen with a broken JVM install.
            throw new IllegalStateException("SHA-512 is not available on this JVM", e);
        }
    }
}
