package com.shiporbit.backend.rate.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ekart Elite live API (rate-aggregator.ekart in application.yaml). Secrets come from env vars only.
 * API reference: https://app.elite.ekartlogistics.in/api/docs
 *
 * @param clientId               path variable of the token API (EKART_CLIENT_ID)
 * @param loginEndpoint          POST, body {username, password} -> {access_token, expires_in (s), token_type}
 * @param serviceabilityEndpoint GET per pincode -> {status, pincode, remark, details{cod, max_cod_amount, forward_pickup, forward_drop, ...}}
 * @param rateEndpoint           POST estimate -> {shippingCharge, fuelSurcharge, codCharge, qcCharge, taxes, total, ...} (strings)
 * @param billingClientType      required by the estimate API; value to be confirmed with Ekart (not documented)
 * @param shippingDirection      required by the estimate API; FORWARD assumed until Ekart confirms
 */
@ConfigurationProperties(prefix = "rate-aggregator.ekart")
public record EkartConfigProperties(
        String username,
        String password,
        String clientId,
        String routerUrl,
        String loginEndpoint,
        String serviceabilityEndpoint,
        String rateEndpoint,
        String billingClientType,
        String shippingDirection,
        Integer connectTimeoutSeconds,
        Integer readTimeoutSeconds) {

    public EkartConfigProperties {
        connectTimeoutSeconds = connectTimeoutSeconds == null ? 2 : connectTimeoutSeconds;
        readTimeoutSeconds = readTimeoutSeconds == null ? 6 : readTimeoutSeconds;
        shippingDirection = shippingDirection == null || shippingDirection.isBlank() ? "FORWARD" : shippingDirection.trim();
    }

    /** Credentials present? Without them the client answers UNKNOWN / PARTNER_NOT_CONFIGURED instead of calling Ekart. */
    public boolean configured() {
        return notBlank(username) && notBlank(password) && notBlank(clientId) && notBlank(routerUrl);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
