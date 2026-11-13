package com.shiporbit.backend.rate.provider;

// Imports: partner client contract, error codes, Flow logging, engine records, MarginTaxPolicy, concurrency.
import com.shiporbit.backend.dto.Dimension;
import com.shiporbit.backend.error.ErrorCode;
import com.shiporbit.backend.logging.Flow;
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
import com.shiporbit.backend.rate.serviceability.Serviceability;
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
// Spring bean.
@Component
// Step 3d: live partner APIs (Delhivery, Shree Maruti) -> same QuoteResult as rate cards.
public class LivePartnerQuoteProvider {

    // Logger.
    private static final Logger LOGGER = LoggerFactory.getLogger(LivePartnerQuoteProvider.class);

    /**
     * Live client partnerCode() -> SO_DELIVERY_PARTNER_LIST.ID.
     * The ShreeMurti client is partner 13 (SHREE_MARUTI): same company, confirmed 2026-10-11.
     * UPS/DHL are not in the partner table, so they are not included here.
     */
    // Client code -> partner id in SO_DELIVERY_PARTNER_LIST:
    private static final Map<String, Integer> PARTNER_ID_BY_CLIENT_CODE = Map.of(
            // Delhivery = 1,
            "delhivery", 1,
            // Shree Maruti = 13,
            "ShreeMaruti", 13,
            // Ekart = 10 (Ekart Elite API; its rate card is retired in V17).
            "ekart", 10);

    // Service code shown for live results.
    public static final String SERVICE_CODE = "LIVE_API";

    // Delhivery payment_mode values: "prepaid" confirmed; "cod" accepted (2026-10-11, needs cod_amount).
    // Delhivery payment_mode prepaid.
    private static final String DELHIVERY_PAYMENT_PREPAID = "prepaid";
    // Delhivery payment_mode cod.
    private static final String DELHIVERY_PAYMENT_COD = "cod";
    // kg -> g.
    private static final BigDecimal GRAMS_PER_KG = BigDecimal.valueOf(1000);

    // partnerId -> client, in registration order.
    private final Map<Integer, DeliveryPartnerClient> clientByPartnerId = new LinkedHashMap<>();
    // Same margin + GST policy as rate cards.
    private final MarginTaxPolicy marginTaxPolicy;
    // Per-partner timeout (shiporbit.rates.live-timeout-seconds, default 10).
    private final long timeoutSeconds;
    // Delhivery freight mode (shiporbit.rates.delhivery-freight-mode, default fod).
    private final String delhiveryFreightMode;
    // One virtual thread per partner call.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // Spring injects every DeliveryPartnerClient bean (Delhivery, ShreeMurti, UPS, DHL), the policy
    public LivePartnerQuoteProvider(List<DeliveryPartnerClient> clients, MarginTaxPolicy marginTaxPolicy,
                                    // and the timeout
                                    @Value("${shiporbit.rates.live-timeout-seconds:10}") long timeoutSeconds,
                                    // and freight mode from application.yaml.
                                    @Value("${shiporbit.rates.delhivery-freight-mode:fod}") String delhiveryFreightMode) {
        // For each client:
        for (DeliveryPartnerClient client : clients) {
            // its partner id (null for UPS / DHL - not in the partner table).
            Integer partnerId = PARTNER_ID_BY_CLIENT_CODE.get(client.partnerCode());
            // Known partner?
            if (partnerId != null) {
                // Register it.
                clientByPartnerId.put(partnerId, client);
            }
        }
        // Store policy.
        this.marginTaxPolicy = marginTaxPolicy;
        // Store timeout.
        this.timeoutSeconds = timeoutSeconds;
        // Store freight mode.
        this.delhiveryFreightMode = delhiveryFreightMode;
        // Startup log: which partner ids are live.
        LOGGER.info("Live rate APIs for partner ids {}", clientByPartnerId.keySet());
    }

    /** Partner ids priced by live API; their rate-card services are skipped. */
    // Used in Step 3a: ids whose rate cards are skipped.
    public Set<Integer> livePartnerIds() {
        // Registered partner ids.
        return clientByPartnerId.keySet();
    }

    /**
     * Quotes every live partner allowed by the request's partners filter, in parallel.
     * @param partnerNames SO_DELIVERY_PARTNER_LIST id -> name (used for the filter and the response)
     * @param partnerAllowed partner name -> may be quoted (shiporbit.partners.enabled); others are not called at all
     */
    // Step 3d entry (called from RateQuoteService.quoteAll).
    public List<QuoteResult> quote(QuoteRequest request, Map<Integer, String> partnerNames,
                                   Predicate<String> partnerAllowed) {
        // Partners to call.
        Map<Integer, DeliveryPartnerClient> selected = new LinkedHashMap<>();
        // For each registered live partner:
        clientByPartnerId.forEach((partnerId, client) -> {
            // its DB name (fallback: client's own name),
            String name = partnerNames.getOrDefault(partnerId, client.partnerName());
            // asked for in the request and allowed by shiporbit.partners.enabled / disabled?
            if (request.includesPartner(name) && partnerAllowed.test(name)) {
                // Select it.
                selected.put(partnerId, client);
            }
        });
        // None selected?
        if (selected.isEmpty()) {
            // Log
            Flow.step(LOGGER, "Live partner APIs: none selected (request partners filter / shiporbit.partners)");
            // and return no results.
            return List.of();
        }

        // Results of the live calls.
        List<QuoteResult> results = new ArrayList<>();
        // Nested Flow scope for the parallel calls.
        try (Flow.Scope ignored = Flow.begin(LOGGER, "Live partner APIs: {} partner(s) in parallel, timeout {}s",
                selected.size(), timeoutSeconds)) {
            // partnerId -> pending result.
            Map<Integer, Future<QuoteResult>> futures = new LinkedHashMap<>();
            // partnerId -> reserved log branch (keeps step numbers stable across threads).
            Map<Integer, Flow.Branch> branches = new LinkedHashMap<>();
            // For each selected partner:
            selected.forEach((partnerId, client) -> {
                // its name,
                String name = partnerNames.getOrDefault(partnerId, client.partnerName());
                // Step number reserved here (submission order), sub-steps logged by the worker thread.
                // reserve a step number for it (e.g. 3d1, 3d2),
                Flow.Branch branch = Flow.branch(LOGGER, "{}: live quote started", name);
                // remember the branch,
                branches.put(partnerId, branch);
                // submit the call on a virtual thread:
                futures.put(partnerId, executor.submit(() -> {
                    // inside the partner's branch (MDC trace_id + step carried to the thread),
                    try (Flow.Scope s = branch.enter()) {
                        // run quoteOne() = serviceability check -> rate call -> margin + GST.
                        return quoteOne(request, partnerId, name, client);
                    }
                }));
            });
            // Then wait for each one, in order:
            futures.forEach((partnerId, future) -> {
                // Timeout / failure lines are logged under that partner's step, not the parent's.
                // inside its branch,
                try (Flow.Scope s = branches.get(partnerId).enter()) {
                    // wait up to timeoutSeconds and add the result (Priced or Unavailable),
                    results.add(await(future, partnerId,
                            // named by DB name.
                            partnerNames.getOrDefault(partnerId, clientByPartnerId.get(partnerId).partnerName())));
                }
            });
            // Count priced.
            long priced = results.stream().filter(r -> r instanceof QuoteResult.Priced).count();
            // Log summary.
            Flow.step(LOGGER, "Live partner APIs done: {} priced, {} unavailable", priced, results.size() - priced);
        }
        // Back to RateQuoteService.
        return results;
    }

    // One live partner, runs on a virtual thread.
    private QuoteResult quoteOne(QuoteRequest request, Integer partnerId, String name, DeliveryPartnerClient client) {
        // Only FORWARD supported (compare always sends FORWARD).
        if (request.direction() != ShipmentDirection.FORWARD) {
            // -> Unavailable otherwise.
            return unavailable(partnerId, name, QuoteStatus.RATE_UNAVAILABLE,
                    "Live API quotes FORWARD shipments only");
        }
        // Only one package line supported (compare always sends one).
        if (request.packages().size() != 1) {
            // -> Unavailable otherwise.
            return unavailable(partnerId, name, QuoteStatus.VALIDATION_ERROR,
                    "Live API supports one package type per request (same box size); got " + request.packages().size());
        }

        // QuoteRequest -> client request (grams, pincodes, dimensions, payment mode, invoice value, options).
        RequestParamRecord params = toRequestParams(request, client.partnerCode());
        // Start timer.
        long t0 = System.nanoTime();
        // Serviceability answer.
        Serviceability serviceability;
        // Nested step: serviceability check.
        try (Flow.Scope s = Flow.begin(LOGGER, "{}: serviceability check {} -> {} ({} g, {})", name,
                params.sourcePinCode(), params.destinationPinCode(), params.weight(), request.paymentMode())) {
            // Partner client's own serviceability API (cached 12 h in ServiceabilityCache). Delhivery: B2C pin-codes API (< 20 kg) or B2B pincode-service; Shree Maruti: ecomm serviceability.
            serviceability = client.checkServiceability(params);
        }
        // Elapsed ms.
        long serviceabilityMs = (System.nanoTime() - t0) / 1_000_000;
        // Decide on the answer:
        switch (serviceability.status()) {
            // NOT_SERVICEABLE:
            case NOT_SERVICEABLE -> {
                // log warning
                Flow.stepWarn(LOGGER, "{}: NOT_SERVICEABLE [{}] {} ({} ms) - rate not fetched", name,
                        code(serviceability.errorCode()), serviceability.reason(), serviceabilityMs);
                // -> Unavailable NOT_SERVICEABLE with the partner's error code; rate API is NOT called.
                return unavailable(partnerId, name, QuoteStatus.NOT_SERVICEABLE, serviceability.errorCode(),
                        serviceability.reason());
            }
            // Strict: rates are fetched only when both pincodes are confirmed serviceable.
            // UNKNOWN (serviceability API error / unreadable answer) -> no rate call, partner shown as unavailable.
            // UNKNOWN (API error / unreadable answer):
            case UNKNOWN -> {
                // log warning
                Flow.stepWarn(LOGGER, "{}: serviceability NOT CONFIRMED [{}] {} ({} ms) - rate not fetched", name,
                        code(serviceability.errorCode()), serviceability.reason(), serviceabilityMs);
                // -> Unavailable PARTNER_API_ERROR (SO-SVC "serviceability unconfirmed" when no specific code); rate API NOT called.
                return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR,
                        serviceability.errorCode() == null ? ErrorCode.SERVICEABILITY_UNCONFIRMED : serviceability.errorCode(),
                        "Serviceability could not be confirmed: " + serviceability.reason());
            }
            // SERVICEABLE: log and continue.
            case SERVICEABLE -> Flow.step(LOGGER, "{}: SERVICEABLE ({}) ({} ms)", name, serviceability.reason(), serviceabilityMs);
        }
        // Start rate timer.
        long t1 = System.nanoTime();
        // Partner's rate answer.
        RateResponse rate;
        // Nested step: rate call.
        try (Flow.Scope s = Flow.begin(LOGGER, "{}: fetching rate", name)) {
            // Partner client's rate API (Delhivery: B2C invoice charges < 20 kg, else LTL; Shree Maruti: rate API). Errors throw PartnerApiException.
            rate = client.getRate(params);
        }
        // Partner answer -> Priced (lines + ShipOrbit margin + GST).
        QuoteResult priced = toPriced(partnerId, name, rate);
        // Log partner total vs customer total.
        Flow.step(LOGGER, "{}: priced - partner total {} (GST {}), customer total {} ({} ms)", name,
                rate.finalFreight(), rate.gst(), ((QuoteResult.Priced) priced).total(), (System.nanoTime() - t1) / 1_000_000);
        // Return to the waiting thread.
        return priced;
    }

    // Wait for one partner's future.
    private QuoteResult await(Future<QuoteResult> future, Integer partnerId, String name) {
        // Try:
        try {
            // block up to the timeout.
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        // Too slow:
        } catch (TimeoutException e) {
            // cancel (interrupts the virtual thread),
            future.cancel(true);
            // log warning,
            Flow.stepWarn(LOGGER, "{}: timed out after {}s [{}]", name, timeoutSeconds, ErrorCode.PARTNER_TIMEOUT.code());
            // -> Unavailable PARTNER_API_ERROR / PARTNER_TIMEOUT.
            return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR, ErrorCode.PARTNER_TIMEOUT,
                    "Partner API timed out after " + timeoutSeconds + "s");
        // Interrupted while waiting:
        } catch (InterruptedException e) {
            // restore interrupt flag,
            Thread.currentThread().interrupt();
            // cancel,
            future.cancel(true);
            // -> Unavailable.
            return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR, "Interrupted while waiting for partner API");
        // The call threw:
        } catch (ExecutionException e) {
            // Deliberate recovery boundary: one partner's failure must not fail the whole quote.
            // real exception.
            Throwable cause = e.getCause();
            // Known partner API error (HTTP error, bad body ...)?
            if (cause instanceof PartnerApiException pae) {
                // log with its error code and HTTP status,
                Flow.stepWarn(LOGGER, "{}: failed [{}] (HTTP {}): {}", name, pae.errorCode().code(), pae.httpStatus(), pae.getMessage());
                // -> Unavailable with that code.
                return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR, pae.errorCode(), pae.getMessage());
            }
            // Anything else: log with stack trace,
            Flow.stepError(LOGGER, "{}: unexpected error [{}]", name, ErrorCode.INTERNAL_ERROR.code(), cause);
            // -> Unavailable INTERNAL_ERROR (one partner never fails the whole quote).
            return unavailable(partnerId, name, QuoteStatus.PARTNER_API_ERROR, ErrorCode.INTERNAL_ERROR,
                    "Unexpected error from partner API: " + cause.getClass().getSimpleName());
        }
    }

    // ---- mapping ----

    /** QuoteRequest -> the existing live-client request. Weight is sent in grams (clients' contract). */
    // QuoteRequest -> RequestParamRecord.
    private RequestParamRecord toRequestParams(QuoteRequest request, String clientCode) {
        // The single package.
        ShipmentPackage p = request.packages().get(0);
        // Dimension DTO:
        Dimension dimension = new Dimension();
        // length cm,
        dimension.setLength(p.lengthCm().doubleValue());
        // width cm,
        dimension.setWidth(p.widthCm().doubleValue());
        // height cm,
        dimension.setHeight(p.heightCm().doubleValue());
        // box count.
        dimension.setBoxCount(p.count());

        // Total dead weight in grams.
        double weightGrams = p.totalDeadWeightKg().multiply(GRAMS_PER_KG).doubleValue();
        // Is this the Delhivery client?
        boolean isDelhivery = "delhivery".equals(clientCode);

        // Partner-specific options; each client reads only its own keys and ignores the rest.
        //   ShreeMurti: cod, deliveryMode, insuranceAmount.  Delhivery: codAmount (sent as cod_amount when COD).
        // Options map read by each client:
        Map<String, Object> partnerOptions = Map.of(
                // COD flag,
                "cod", request.isCod(),
                // delivery mode,
                "deliveryMode", "SURFACE",
                // insurance (declared) value,
                "insuranceAmount", request.declaredValue().doubleValue(),
                // COD amount.
                "codAmount", request.codAmount().doubleValue());

        // Build the client request:
        return new RequestParamRecord(
                // weight (g),
                weightGrams,
                // origin pincode,
                request.originPincode(),
                // destination pincode,
                request.destinationPincode(),
                // dimensions,
                dimension,
                // cheque payment: no,
                false,                                                  // chequePayment
                // Delhivery payment mode (cod / prepaid), others null,
                isDelhivery ? (request.isCod() ? DELHIVERY_PAYMENT_COD : DELHIVERY_PAYMENT_PREPAID) : null,
                // invoice value,
                request.declaredValue().doubleValue(),                  // invoice amount
                // Delhivery freight mode, others null,
                isDelhivery ? delhiveryFreightMode : null,
                // ROV insurance: no,
                false,                                                  // rov_insurance: real Delhivery curl sent false (see change.md)
                // options.
                partnerOptions);
    }

    /** RateResponse -> Priced. Lines reconcile to the partner's pre-tax total; any gap goes to ADJUSTMENT. */
    // Partner answer -> Priced.
    private QuoteResult toPriced(Integer partnerId, String name, RateResponse rate) {
        // Pre-tax lines.
        List<ChargeLine> lines = new ArrayList<>();
        // BASE = partner base freight (0 when missing).
        lines.add(new ChargeLine(ChargeLine.BASE, nz(rate.baseFreight())));
        // Fuel hike (if non-zero).
        addIfNonZero(lines, "FUEL_HIKE", rate.fuelHike());
        // Fuel surcharge.
        addIfNonZero(lines, "FUEL_SURCHARGE", rate.surcharge());
        // ROV insurance.
        addIfNonZero(lines, "ROV", rate.insuranceRov());
        // ODA.
        addIfNonZero(lines, "ODA", rate.odaCharge());
        // Handling.
        addIfNonZero(lines, "HANDLING", rate.handlingCharges());

        // Partner pre-tax total = partner final total - partner GST.
        BigDecimal partnerPreTax = nz(rate.finalFreight()).subtract(nz(rate.gst()));
        // Sum of itemised lines.
        BigDecimal itemised = lines.stream().map(ChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Unexplained difference -> ADJUSTMENT line, so lines = partner pre-tax.
        addIfNonZero(lines, "ADJUSTMENT", partnerPreTax.subtract(itemised));

        // ShipOrbit margin on pre-tax, then GST on (pre-tax + margin). Partner's GST figure is replaced by ours.
        MarginTaxPolicy.Totals totals = marginTaxPolicy.apply(lines);
        // Priced: id, name, LIVE_API, SURFACE, no data status, version "LIVE_API",
        return new QuoteResult.Priced(partnerId, name, SERVICE_CODE, ServiceMode.SURFACE, null, SERVICE_CODE,
                // no zone / weights / slab, lines incl. MARGIN + GST, taxable, GST, total.
                null, null, null, null, totals.lines(), totals.taxableAmount(), totals.gst(), totals.total());
    }

    // Error code for logs.
    private static String code(ErrorCode c) {
        // "-" when none.
        return c == null ? "-" : c.code();
    }

    // Add a line only when non-zero.
    private static void addIfNonZero(List<ChargeLine> lines, String code, BigDecimal amount) {
        // Non-null and non-zero?
        if (amount != null && amount.signum() != 0) {
            // Add.
            lines.add(new ChargeLine(code, amount));
        }
    }

    // null -> 0.
    private static BigDecimal nz(BigDecimal v) {
        // Return.
        return v == null ? BigDecimal.ZERO : v;
    }

    // Unavailable without an error code
    private static QuoteResult.Unavailable unavailable(Integer partnerId, String name, QuoteStatus status, String reason) {
        // (code derived from the status).
        return unavailable(partnerId, name, status, null, reason);
    }

    // Unavailable with an error code:
    private static QuoteResult.Unavailable unavailable(Integer partnerId, String name, QuoteStatus status,
                                                       ErrorCode errorCode, String reason) {
        // LIVE_API / SURFACE, status, reason,
        return new QuoteResult.Unavailable(partnerId, name, SERVICE_CODE, ServiceMode.SURFACE, null, status, reason,
                // code string (null -> derived from status in QuoteResult.Unavailable).
                errorCode == null ? null : errorCode.code());
    }

    // On app shutdown:
    @PreDestroy
    void shutdown() {
        // stop the executor.
        executor.shutdownNow();
    }
}
