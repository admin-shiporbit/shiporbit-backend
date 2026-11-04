package com.shiporbit.backend.logging;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks secrets and personal data in any text before it is written to a log.
 * Applied to EVERY log line (message and stack trace) by MaskingMessageConverter /
 * MaskingThrowableConverter, so a careless log statement still cannot leak a secret.
 * Rules (in order):
 * <ol>
 *   <li>Authorization schemes: "Bearer xxx", "Token xxx", "Basic xxx" (value of 8+ chars with a digit) -> scheme + ****</li>
 *   <li>JWTs anywhere (eyJ...x.y.z) -> eyJ****</li>
 *   <li>Secret-named keys in JSON / key=value / headers: password, secret, token, api-key, otp, jwt ... -> ****</li>
 *   <li>Emails -> first 2 chars + ***@domain</li>
 *   <li>Indian mobile numbers (10 digits starting 6-9, optional +91) -> first 2 + ****** + last 2</li>
 *   <li>Card numbers (15-16 digits passing the Luhn check) -> **** + last 4. Other long numbers
 *       (timestamps, AWB numbers, ids) are kept.</li>
 * </ol>
 * Pincodes, amounts, weights and ids are left as they are - they are needed to follow the flow.
 */
public final class LogMasker {

    private static final String MASK = "****";

    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "(?i)\\b(Bearer|Token|Basic)\\s+(?!\\*{4})(?=[A-Za-z0-9._~+/=\\-]*\\d)[A-Za-z0-9._~+/=\\-]{8,}");

    private static final Pattern JWT = Pattern.compile(
            "eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]+");

    // key, separator (: or = with optional quotes/spaces), value (until quote, comma, space, &, } or ])
    private static final Pattern SECRET_KV = Pattern.compile(
            "(?i)([\"']?(?:password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|apikey|x-api-key|"
                    + "access[_-]?token|refresh[_-]?token|id[_-]?token|auth[_-]?token|token|jwt|otp|pin[_-]?code[_-]?otp|"
                    + "authorization|cookie|set-cookie|session[_-]?id)[\"']?\\s*[:=]\\s*[\"']?)"
                    + "(?!\\*{4})([^\"'\\s,&}\\]]+)");

    private static final Pattern EMAIL = Pattern.compile(
            "\\b([A-Za-z0-9._%+-]{1,2})[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");

    private static final Pattern MOBILE = Pattern.compile(
            "(?<![\\d.])(\\+?91[\\s-]?)?([6-9]\\d)(\\d{6})(\\d{2})(?![\\d.])");

    private static final Pattern CARD = Pattern.compile(
            "(?<![\\d.])\\d{15,16}(?![\\d.])");

    private static final List<Rule> RULES = List.of(
            new Rule(AUTH_SCHEME, m -> m.group(1) + " " + MASK),
            new Rule(JWT, m -> "eyJ" + MASK),
            new Rule(SECRET_KV, m -> m.group(1) + MASK),
            new Rule(EMAIL, m -> m.group(1) + "***@" + m.group(2)),
            new Rule(MOBILE, m -> (m.group(1) == null ? "" : m.group(1)) + m.group(2) + "******" + m.group(4)),
            new Rule(CARD, m -> luhn(m.group()) ? MASK + m.group().substring(m.group().length() - 4) : m.group()));

    private LogMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Rule rule : RULES) {
            out = rule.apply(out);
        }
        return out;
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    private record Rule(Pattern pattern, java.util.function.Function<Matcher, String> replacement) {
        String apply(String text) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) {
                return text;
            }
            StringBuilder sb = new StringBuilder(text.length());
            do {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement.apply(m)));
            } while (m.find());
            m.appendTail(sb);
            return sb.toString();
        }
    }
}
