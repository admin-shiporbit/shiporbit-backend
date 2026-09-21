package com.shiporbit.backend.payment.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * These tests independently re-derive the expected SHA-512 hex digest for a fixed pipe-joined
 * string built directly from the field values (not by calling PayUHashUtil), then compare it to
 * PayUHashUtil's output. A field-order or pipe-count bug in the production code would have to
 * accidentally match this separate, deliberately literal construction to pass.
 */
class PayUHashUtilTest {

    @Test
    void requestHashMatchesPayUsDocumentedFieldOrder() throws Exception {
        // key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||salt
        String literal = "KEY123" + "|" + "txn-1" + "|" + "100.00" + "|" + "Wallet Top-up" + "|" + "Saurabh"
                + "|" + "user@example.com" + "|" + "udf1val" + "|" + "" + "|" + "" + "|" + "" + "|" + ""
                + "||||||" + "key-salt";
        String expected = sha512Hex(literal);

        String actual = PayUHashUtil.requestHash(
                "KEY123", "txn-1", "100.00", "Wallet Top-up", "Saurabh", "user@example.com",
                "udf1val", null, null, null, null,
                "key-salt"
        );

        assertEquals(expected, actual);
    }

    @Test
    void requestHashChangesWhenAmountIsTampered() {
        String original = PayUHashUtil.requestHash(
                "KEY123", "txn-1", "100.00", "Wallet Top-up", "Saurabh", "user@example.com",
                null, null, null, null, null, "key-salt"
        );
        String tampered = PayUHashUtil.requestHash(
                "KEY123", "txn-1", "999.00", "Wallet Top-up", "Saurabh", "user@example.com",
                null, null, null, null, null, "key-salt"
        );
        assertNotEquals(original, tampered);
    }

    @Test
    void responseHashMatchesPayUsDocumentedReverseFieldOrder() throws Exception {
        // SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key
        String literal = "key-salt" + "|" + "success" + "||||||"
                + "" + "|" + "" + "|" + "" + "|" + "" + "|" + ""
                + "|" + "user@example.com" + "|" + "Saurabh" + "|" + "Wallet Top-up" + "|" + "100.00"
                + "|" + "txn-1" + "|" + "KEY123";
        String expected = sha512Hex(literal);

        String actual = PayUHashUtil.responseHash(
                "key-salt", "success",
                null, null, null, null, null,
                "user@example.com", "Saurabh", "Wallet Top-up", "100.00", "txn-1", "KEY123"
        );

        assertEquals(expected, actual);
    }

    @Test
    void responseHashRejectsTamperedStatus() {
        String success = PayUHashUtil.responseHash(
                "key-salt", "success", null, null, null, null, null,
                "user@example.com", "Saurabh", "Wallet Top-up", "100.00", "txn-1", "KEY123"
        );
        String failure = PayUHashUtil.responseHash(
                "key-salt", "failure", null, null, null, null, null,
                "user@example.com", "Saurabh", "Wallet Top-up", "100.00", "txn-1", "KEY123"
        );
        assertNotEquals(success, failure);
    }

    private static String sha512Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
