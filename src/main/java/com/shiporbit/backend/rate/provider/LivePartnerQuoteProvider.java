package com.shiporbit.backend.rate.provider;

import com.shiporbit.backend.dto.Dimension;
import com.shiporbit.backend.exception.PartnerApiException;
import com.shiporbit.backend.rate.core.ChargeLine;
import com.shiporbit.backend.rate.core.QuoteRequest;
import com.shiporbit.backend.rate.core.QuoteResult;
import com.shiporbit.backend.rate.core.QuoteStatus;
import com.shiporbit.backend.rate.core.ShipmentPackage;
import com.shiporbit.backend.rate.dto.request.RequestParamRecord;
import com.shiporbit.backend.rate.dto.response.RateResponse;
import com.shiporbit.backend.rate.engine.MarginTaxPolicy;
import com.shiporbit.backend.rate.entity.ServiceMode;
import com.shiporbit.backend.rate.entity.ShipmentDirection;
import com.shiporbit.backend.rate.service.DeliveryPartnerClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Adapter: prices partners that have a live rate API through the existing DeliveryPartnerClient
 * implementations, and returns the same QuoteResult type as the rate-card calculators.
 * <ul>
 *   <li>Partners listed here are NOT priced from rate cards (RateQuoteService skips their services).</li>
 *   <li>Calls run in parallel on virtual threads with a timeout (shiporbit.rates.live-timeout-seconds, default 10).</li>
 *   <li>Margin and GST use MarginTaxPolicy, same as rate cards: margin on the partner's pre-tax amount
 *       (partner total - partner GST), then GST on (pre-tax + margin). The partner's own GST figure is replaced.</li>
 *   <li>Failures never drop a partner: they come back as Unavailable with a status and reason.</li>
 * </ul>
 */
@Component
public class LivePartnerQuoteProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivePartnerQuoteProvider.class);

    /**
     * Live client partnerCode() -> SO_DELIVERY_PARTNER_LIST.ID.
     * The ShreeMurti client is partner 13 (SHREE_MARUTI): same company, confirmed 2026-10-11.
     * UPS/DHL are not in the partner table, so they are not included here.
     */
    private static final Map<String, Integer> PARTNER_ID_BY_CLIENT_CODE = Map.of(
            "delhivery", 1,
            "ShreeMurti", 13);

    public static final String SERVICE_CODE = "LIVE_API";

    // Delhivery payment_mode values: "prepaid" confirmed; "cod" accepted (2026-10-11, needs cod_amount).
    private static final String DELHIVERY_PAYMENT_PREPAID = "prepaid";
    private static final String DELHIVERY_PAYMENT_COD = "cod";
    private static final BigDecimal GRAMS_PER_KG = BigDecimal.valueOf(1000);

    private final Map<Integer, DeliveryPartnerClient> clientByPartnerId = new LinkedHashMap<>();
    private final MarginTaxPolicy marginTaxPolicy;
    private final long timeoutSeconds;
    private final String delhiveryFreightMode;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LivePartnerQuoteProvider(List<DeliveryPartnerClient> clients, MarginTaxPolicy marginTaxPolicy,
                                    @Value("${shiporbit.rates.live-timeout-seconds:10}") long timeoutSeconds,
                                    @Value("${shiporbit.rates.delhivery-freight-mode:fod}") String delhiveryFreightMode) {
        for (DeliveryPartnerClient client : clients) {
            Integer partnerId = PARTNER_ID_BY_CLIENT_CODE.get(client.partnerCode());
            if (partnerId != null) {
                clientByPartnerId.put(partnerId, client);
            }
        }
        this.marginTaxPolicy = marginTaxPolicy;
        this.timeoutSeconds = timeoutSeconds;
        this.delhiveryFreightMode = delhiveryFreightMode;
        LOGGER.info("Live rate APIs for partner ids {}", clientByPartnerId.keySet());
    }

    /** Partner ids priced by live API; their rate-card services are skipped. */
    public Set<Integer> livePartnerIds() {
        return clientByPartnerId.keySet();
    }

    /**
     * Quotes every live partner allowed by the request's partners filter, in parallel.
     * @param partnerNames SO_DELIVERY_PARTNER_LIST id -> name (used for the filter and the response)
     * @param partnerAllowed partner name -> may be quoted (shiporbit.partners.enabled); others are not called at all
     */
    public List<QuoteResult> quote(QuoteRequest request, Map<Integer, String> partnerNames,
                                   Predicate<String> partnerAllowed) {
        Map<Integer, Future<QuoteResult>> futures = new LinkedHashMap<>();
        clientByPartnerId.forEach((partnerId, client) -> {
            String name = partnerNames.getOrDefault(partnerId, client.partnerName());
            if (request.includesPartner(name) && partnerAllowed.test(name)) {
                futures.put(partnerId, executor.submit(() -> quoteOne(request, partnerId, name, client)));
            }
        });

        List<QuoteResult> results = new ArrayList<>();
        futures.forEach((partnerId, future) -> results.add(await(future, partnerId,
                partnerNames.getOrDefault(partnerId, clientByPartnerId.get(partnerId).partnerName()))));
        return results;
    }

    private QuoteResult quoteOne(QuoteRequest request, Integer partnerId, String name, DeliveryPartnerClient client) {
        if (request.direction() != ShipmentDirection.FORWARD) {
            return unavailable(partnerId, name, QuoteStatus.RATE_UNAVAILABLE,
                    "Live API quotes FORWARD shipments only");
        }
        if (request.packages().size() != 1) {
            return unavailable(partnerId, name, QuoteStatus.VALIDATION_ERROR,
                    "Live API supports one package type per request (same box size); got " + request.packages().size());
        }

        RequestParamRecord params = toRequestParams(request, client.partnerCode());
        if (!client.isServiceable(params)) {
            return unavailable(partnerId, name, QuoteStatus.NOT_SERVICEABLE, "Partner API: lane not serviceable");
        }
        RateResponse rate = client.getRate(params);
        return toPriced(partnerId, name, rate);
    }

    private QuoteResult await(Future<QuoteResult> future, Integer partnerId, String name) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.warn("Live rate API for partner {} timed out after {}s", name, timeoutSeconds);
            return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR,
                    "Partner API timed out after " + timeoutSeconds + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR, "Interrupted while waiting for partner API");
        } catch (ExecutionException e) {
            // Deliberate recovery boundary: one partner's failure must not fail the whole quote.
            Throwable cause = e.getCause();
            if (cause instanceof PartnerApiException pae) {
                LOGGER.warn("Live rate API for partner {} failed (HTTP {}): {}", name, pae.httpStatus(), pae.getMessage());
                return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR, pae.getMessage());
            }
            LOGGER.error("Unexpected error from live rate API for partner {}", name, cause);
            return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR,
                    "Unexpected error from partner API: " + cause.getClass().getSimpleName());
        }
    }

    // ---- mapping ----

    /** QuoteRequest -> the existing live-client request. Weight is sent in grams (clients' contract). */
    private RequestParamRecord toRequestParams(QuoteRequest request, String clientCode) {
        ShipmentPackage p = request.packages().get(0);
        Dimension dimension = new Dimension();
        dimension.setLength(p.lengthCm().doubleValue());
        dimension.setWidth(p.widthCm().doubleValue());
        dimension.setHeight(p.heightCm().doubleValue());
        dimension.setBoxCount(p.count());

        double weightGrams = p.totalDeadWeightKg().multiply(GRAMS_PER_KG).doubleValue();
        boolean isDelhivery = "delhivery".equals(clientCode);

        // Partner-specific options; each client reads only its own keys and ignores the rest.
        //   ShreeMurti: cod, deliveryMode, insuranceAmount.  Delhivery: codAmount (sent as cod_amount when COD).
        Map<String, Object> partnerOptions = Map.of(
                "cod", request.isCod(),
                "deliveryMode", "SURFACE",
                "insuranceAmount", request.declaredValue().doubleValue(),
                "codAmount", request.codAmount().doubleValue());

        return new RequestParamRecord(
                weightGrams,
                request.originPincode(),
                request.destinationPincode(),
                dimension,
                false,                                                  // chequePayment
                isDelhivery ? (request.isCod() ? DELHIVERY_PAYMENT_COD : DELHIVERY_PAYMENT_PREPAID) : null,
                request.declaredValue().doubleValue(),                  // invoice amount
                isDelhivery ? delhiveryFreightMode : null,
                false,                                                  // rov_insurance: real Delhivery curl sent false (see change.md)
                partnerOptions);
    }

    /** RateResponse -> Priced. Lines reconcile to the partner's pre-tax total; any gap goes to ADJUSTMENT. */
    private QuoteResult toPriced(Integer partnerId, String name, RateResponse rate) {
        List<ChargeLine> lines = new ArrayList<>();
        lines.add(new ChargeLine(ChargeLine.BASE, nz(rate.baseFreight())));
        addIfNonZero(lines, "FUEL_HIKE", rate.fuelHike());
        addIfNonZero(lines, "FUEL_SURCHARGE", rate.surcharge());
        addIfNonZero(lines, "ROV", rate.insuranceRov());
        addIfNonZero(lines, "ODA", rate.odaCharge());
        addIfNonZero(lines, "HANDLING", rate.handlingCharges());

        BigDecimal partnerPreTax = nz(rate.finalFreight()).subtract(nz(rate.gst()));
        BigDecimal itemised = lines.stream().map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        addIfNonZero(lines, "ADJUSTMENT", partnerPreTax.subtract(itemised));

        MarginTaxPolicy.Totals totals = marginTaxPolicy.apply(lines);
        return new QuoteResult.Priced(partnerId, name, SERVICE_CODE, ServiceMode.SURFACE, null, SERVICE_CODE,
                null, null, null, null, totals.lines(), totals.taxableAmount(), totals.gst(), totals.total());
    }

    private static void addIfNonZero(List<ChargeLine> lines, String code, BigDecimal amount) {
        if (amount != null && amount.signum() != 0) {
            lines.add(new ChargeLine(code, amount));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static QuoteResult.Unavailable unavailable(Integer partnerId, String name, QuoteStatus status, String reason) {
        return new QuoteResult.Unavailable(partnerId, name, SERVICE_CODE, ServiceMode.SURFACE, null, status, reason);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
