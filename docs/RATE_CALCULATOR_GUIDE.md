# Rate Calculator: Design and Development Guide

Scope: rate-card based rate calculator for all delivery partners, built into the existing
`rate` module. Follows `CODING_PRACTICES.md`. Partner live APIs stay as they are and plug
into the same contract.

---

## 1. Verdict

- **Keep your idea** (interface + abstract class with the fixed flow + abstract steps).
  Apply it per **pricing model**, not per partner. Partners are rows in the database.
- **Keep the existing aggregator design** (`DeliveryPartnerClient` list-injected into
  `RateAggregatorServiceImpl`, `MarkupPartnerClient` as decorator). Extend it so rate-card
  partners and live-API partners are compared side by side.
- **No new framework needed.** Your stack already has JPA, Flyway, PostgreSQL, validation,
  RestClient, Actuator. Add only cache and Testcontainers now (section 3).
- **No rules engine** (Drools / Easy Rules). Rules here are arithmetic + order; config +
  strategies is simpler to debug.

---

## 2. What exists today (from the code)

| Area | Current | Keep / change |
|---|---|---|
| `DeliveryPartnerClient` | Strategy interface: `partnerCode`, `isServiceable`, `getRate` | Keep, evolve into `RateProvider` (section 5) |
| `RateAggregatorServiceImpl` | Registry from `List<DeliveryPartnerClient>`, loops partners | Keep pattern; return a result per partner incl. failures |
| `MarkupPartnerClient` | Decorator, hard-coded `1.10` on every line **including GST** | Fix: margin on pre-tax amount, then recompute GST; value from config |
| `compareRates` | Catches `Exception`, logs, drops partner | Return `Unavailable(reason)` instead of dropping |
| `RequestParamRecord` | `Double` weight/amount, single `Dimension`, `partnerOptions` map | New `QuoteRequest` with `BigDecimal` and `List<Package>`; keep `partnerOptions` for live APIs |
| `RateResponse` | Fixed 8 money fields | New response with `List<ChargeLine>` (any charge, no schema change per partner) |
| `rate_card_config_delhivery`, `rate_charge_slab_delhivery`, `rate_oda_exempt_pincode_delhivery` | Partner-specific tables | Do not copy per partner. Replace with generic tables (new migration, V1 untouched) |
| `SO_DELIVEY_PIN_CODE_LIST` | Pincode flags: prepaid, reverse, COD, cash | Missing pickup, delivery, ODA, special-city flags. New `SO_PARTNER_PINCODE` |
| `PincodeEntity.pinCode` | Maps to `pin_code` (Spring naming strategy); table column is `pincode` | Mismatch: fix in the new table/entity |
| `DelhiveryPartnerClient.isServiceable` | `TODO`, returns `true` | Rate-card partners use DB serviceability |
| Endpoint | `POST /api/rate/aggregator` | New `POST /api/v1/rates/quotes`; keep old one until frontend moves |
| `change.md` | Mentions `RateController`, `Zones`, `DelhiveryRateStrategy` | Those classes no longer exist: update the log |

---

## 3. Gradle additions

```groovy
// Cache for rate cards / zone maps (read on every quote, change rarely)
implementation 'org.springframework.boot:spring-boot-starter-cache'
implementation 'com.github.ben-manes.caffeine:caffeine'          // version managed by Boot

// Real PostgreSQL in repository tests (H2 differs from PG on types/indexes)
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:testcontainers-postgresql' // Testcontainers 2.x name;
                                                                  // if unresolved, use 'org.testcontainers:postgresql'
testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
```

Later (not needed to start):

| When | Add | Note |
|---|---|---|
| Admin upload of partner rate card / pincode Excel | `org.apache.poi:poi-ooxml` | Not Boot-managed: pin a 5.x version |
| API docs | `org.springdoc:springdoc-openapi-starter-webmvc-ui` | Use the 3.x line (Boot 4); confirm version on Maven Central |
| Multiple app instances | Redis cache | Caffeine is enough for one instance |

No change needed for: Java 21, Spring Boot 4.1.0, Flyway, PostgreSQL driver, Lombok, validation.
Avoid `jsonb` columns: keeps H2 local runs working. Use typed columns (section 6).

---

## 4. Design patterns

| Pattern | Class(es) | Purpose |
|---|---|---|
| **Template Method** | `AbstractRateCardCalculator.quote()` (`final`) | Fixed flow; subclasses fill the abstract steps |
| **Strategy** (pricing model) | `ZoneSlabCalculator`, `DistanceBandCalculator`, `B2bFreightCalculator`, `HyperlocalCalculator` | 4 models cover all 14 partners |
| **Strategy** (charge type) | `ChargeHandler`: `FlatHandler`, `PercentHandler`, `PerKgHandler`, `TieredHandler` | Any surcharge = a DB row, no new code |
| **Ordered pipeline** | `ChargeEngine` | Applies rules by `seq`; each rule names its base (handles Blue Dart ESS -> fuel on top) |
| **Registry / Factory** | `CalculatorRegistry` (`Map<PricingModel, RateCardCalculator>` from Spring) | Pick calculator by service config, no `if/else` |
| **Specification** | `EligibilityRule` implementations | Serviceable, COD allowed, weight limit, prohibited item; each returns a reason code |
| **Decorator** | `MarginRateProvider` (replaces `MarkupPartnerClient`) | ShipOrbit margin applied once, before GST |
| **Adapter** | Existing `*PartnerClient` classes | Live partner API -> common `QuoteResult` |
| **Result type** | `sealed interface QuoteResult` | `Priced` / `Unavailable(status, reason)`; one partner failing never hides others |
| **Immutable value objects** | records: `QuoteRequest`, `Package`, `ChargeLine`, `Money` rules | Safe, testable calculations |

```
RateQuoteController (/api/v1/rates/quotes)
        |
RateQuoteService ---- runs all RateProviders in parallel (virtual threads, timeout)
        |
RateProvider (interface)
   |-- RateCardProvider  (one per enabled partner service in DB)
   |       |-- EligibilityRules (Specification)
   |       |-- CalculatorRegistry -> AbstractRateCardCalculator (Template Method)
   |                                   |-- ZoneSlabCalculator / DistanceBand / B2bFreight / Hyperlocal
   |                                   |-- ChargeEngine -> ChargeHandlers (Strategy)
   |-- LiveApiProvider   (adapters over existing Delhivery LTL / ShreeMurti / UPS / DHL clients)
   |-- MarginRateProvider (Decorator around each)
```

---

## 5. Package layout

Per `CODING_PRACTICES.md` (`rate` = courier-independent rules, `rate.<partner>` = partner specifics):

```
com.shiporbit.backend.rate
 ├── api            RateQuoteController, request/response DTOs
 ├── core           QuoteRequest, Package, QuoteResult, ChargeLine, enums
 ├── service        RateQuoteService (orchestration, parallel calls)
 ├── provider       RateProvider, RateCardProvider, LiveApiProvider, MarginRateProvider
 ├── engine         AbstractRateCardCalculator, ZoneSlabCalculator, ..., CalculatorRegistry, PricingContext
 ├── engine.charge  ChargeEngine, ChargeHandler + implementations
 ├── engine.rule    EligibilityRule + implementations
 ├── card           JPA entities + repositories for rate tables, RateCardLoader (cached)
 └── partner.<x>    existing live clients (delhivery, shreemurti, ups, dhl) moved here over time
```

---

## 6. Database (new migration `V6__rate_card_schema.sql`)

V1/V2 stay unchanged (append-only). Old `*_delhivery` tables can be dropped in a later
migration once nothing reads them.

```sql
CREATE TABLE SO_RATE_SERVICE (
    ID               BIGSERIAL PRIMARY KEY,
    PARTNER_ID       INT          NOT NULL,
    SERVICE_CODE     VARCHAR(50)  NOT NULL,           -- e.g. SURFACE_05KG
    PRICING_MODEL    VARCHAR(20)  NOT NULL,           -- ZONE_SLAB / DISTANCE_BAND / B2B_FREIGHT / HYPERLOCAL
    MODE             VARCHAR(20)  NOT NULL,           -- SURFACE / AIR / ...
    ENABLED          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT SO_FK_RATE_SERVICE_PARTNER FOREIGN KEY (PARTNER_ID) REFERENCES SO_DELIVERY_PARTNER_LIST (ID),
    CONSTRAINT SO_UQ_RATE_SERVICE UNIQUE (PARTNER_ID, SERVICE_CODE)
);

CREATE TABLE SO_RATE_CARD (
    ID                 BIGSERIAL PRIMARY KEY,
    SERVICE_ID         BIGINT        NOT NULL,
    VERSION_LABEL      VARCHAR(40)   NOT NULL,
    DATA_STATUS        VARCHAR(20)   NOT NULL,        -- SAMPLE / CONTRACT
    EFFECTIVE_FROM     DATE          NOT NULL,
    EFFECTIVE_TO       DATE,
    VOLUMETRIC_DIVISOR NUMERIC(10,2),                 -- null for HYPERLOCAL
    CFT_KG             NUMERIC(10,2),                 -- B2B only
    MIN_CHARGEABLE_KG  NUMERIC(10,3) NOT NULL DEFAULT 0,
    MAX_WEIGHT_KG      NUMERIC(10,3) NOT NULL,
    MPS_RULE           VARCHAR(20)   NOT NULL,        -- TOTAL / PER_PIECE
    RTO_RULE           VARCHAR(20)   NOT NULL,        -- SAME_AS_FORWARD / PERCENT / TABLE
    RTO_PERCENT        NUMERIC(6,2),
    MIN_BILL           NUMERIC(10,2) NOT NULL DEFAULT 0,
    RATES_INCLUDE_GST  BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT SO_FK_RATE_CARD_SERVICE FOREIGN KEY (SERVICE_ID) REFERENCES SO_RATE_SERVICE (ID),
    CONSTRAINT SO_UQ_RATE_CARD UNIQUE (SERVICE_ID, EFFECTIVE_FROM)
);

-- Base freight. LOCATION_KEY = zone ('C'), distance band ('201-500'),
-- zone pair for B2B ('N1-N2'), or vehicle for hyperlocal ('BIKE').
CREATE TABLE SO_RATE_SLAB (
    ID            BIGSERIAL PRIMARY KEY,
    RATE_CARD_ID  BIGINT        NOT NULL,
    DIRECTION     VARCHAR(10)   NOT NULL,             -- FORWARD / RTO / REVERSE
    LOCATION_KEY  VARCHAR(20)   NOT NULL,
    FROM_UNIT     NUMERIC(10,3) NOT NULL,             -- kg (or km for hyperlocal)
    TO_UNIT       NUMERIC(10,3),                      -- null = open-ended
    FIRST_UNIT    NUMERIC(10,3) NOT NULL,             -- first slab size
    FIRST_RATE    NUMERIC(10,2) NOT NULL,
    ADD_UNIT      NUMERIC(10,3) NOT NULL,             -- additional slab size (1 = per kg / per km)
    ADD_RATE      NUMERIC(10,2) NOT NULL,
    CONSTRAINT SO_FK_RATE_SLAB_CARD FOREIGN KEY (RATE_CARD_ID) REFERENCES SO_RATE_CARD (ID)
);
CREATE INDEX SO_IDX_RATE_SLAB_LOOKUP ON SO_RATE_SLAB (RATE_CARD_ID, DIRECTION, LOCATION_KEY);

-- Every charge beyond base freight is one row.
CREATE TABLE SO_RATE_CHARGE_RULE (
    ID              BIGSERIAL PRIMARY KEY,
    RATE_CARD_ID    BIGINT        NOT NULL,
    CHARGE_CODE     VARCHAR(30)   NOT NULL,           -- FSC, COD, ODA, FOV, DOCKET, ESS, ...
    SEQ             INT           NOT NULL,           -- application order
    CALC_TYPE       VARCHAR(20)   NOT NULL,           -- FLAT / PERCENT / PER_KG / TIERED
    RATE_VALUE      NUMERIC(12,4) NOT NULL,
    MIN_AMOUNT      NUMERIC(10,2),
    MAX_AMOUNT      NUMERIC(10,2),
    BASE            VARCHAR(30)   NOT NULL,           -- FREIGHT / RUNNING_TOTAL / COD_AMOUNT / DECLARED_VALUE / CHARGEABLE_KG / CODES
    BASE_CODES      VARCHAR(200),                     -- when BASE = CODES, e.g. 'BASE,ODA,ESS'
    COMBINE         VARCHAR(10)   NOT NULL DEFAULT 'MAX', -- MAX(fixed, %) or SUM for COD-style rules
    FIXED_PART      NUMERIC(10,2),                    -- fixed part for COD-style rules
    TIER_FROM       NUMERIC(12,2),                    -- TIERED: applies when base in [from, to)
    TIER_TO         NUMERIC(12,2),
    APPLIES_WHEN    VARCHAR(30)   NOT NULL,           -- ALWAYS / COD / ODA / SPECIAL_CITY / RTO / ADDON_OTP / RISK_OWNER / ...
    DIRECTIONS      VARCHAR(30)   NOT NULL DEFAULT 'FORWARD,RTO,REVERSE',
    CONSTRAINT SO_FK_RATE_RULE_CARD FOREIGN KEY (RATE_CARD_ID) REFERENCES SO_RATE_CARD (ID)
);

CREATE TABLE SO_ZONE_MAP (
    PARTNER_ID     INT         NOT NULL,
    ORIGIN_PIN     VARCHAR(6)  NOT NULL,
    DEST_PIN       VARCHAR(6)  NOT NULL,
    LOCATION_KEY   VARCHAR(20) NOT NULL,
    CONSTRAINT SO_PK_ZONE_MAP PRIMARY KEY (PARTNER_ID, ORIGIN_PIN, DEST_PIN)
);

CREATE TABLE SO_PARTNER_PINCODE (
    PARTNER_ID      INT        NOT NULL,
    PINCODE         VARCHAR(6) NOT NULL,
    PICKUP          BOOLEAN    NOT NULL,
    DELIVERY        BOOLEAN    NOT NULL,
    COD             BOOLEAN    NOT NULL,
    PREPAID         BOOLEAN    NOT NULL,
    REVERSE_PICKUP  BOOLEAN    NOT NULL,
    ODA             BOOLEAN    NOT NULL,
    SPECIAL_CITY    BOOLEAN    NOT NULL DEFAULT FALSE,
    CONSTRAINT SO_PK_PARTNER_PINCODE PRIMARY KEY (PARTNER_ID, PINCODE)
);
```

Zone map note: a full origin x destination file is large (pincodes squared). Most partners
give rules (state/city/metro lists). Start with the explicit table; add a rule-based
`ZoneResolver` per partner only if a partner gives rules instead of a file.

`V8__seed_sample_rate_cards.sql`: load the SAMPLE rows from `ShipOrbit_Rate_Calculator.xlsx`
(14 services, zone map, pincodes) with `DATA_STATUS = 'SAMPLE'`.

---

## 7. Core code

### 7.1 Request and result

```java
public record QuoteRequest(
        String originPincode,
        String destinationPincode,
        Direction direction,           // FORWARD, RTO, REVERSE
        PaymentMode paymentMode,       // PREPAID, COD
        BigDecimal codAmount,
        BigDecimal declaredValue,
        RiskType riskType,             // OWNER, CARRIER
        List<Package> packages,
        Set<AddOn> addOns,
        BigDecimal distanceKm,         // hyperlocal only; null otherwise
        VehicleType vehicle            // hyperlocal only; null otherwise
) {
    public QuoteRequest {
        packages = List.copyOf(packages);
        addOns = addOns == null ? Set.of() : Set.copyOf(addOns);
        codAmount = codAmount == null ? BigDecimal.ZERO : codAmount;
    }
    public BigDecimal totalDeadWeightKg() { /* sum count * deadWeightKg */ }
    public BigDecimal totalVolumeCm3()   { /* sum count * l * w * h */ }
}

public record Package(int count, BigDecimal deadWeightKg,
                      BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) { }

public record ChargeLine(String code, BigDecimal amount) { }

public sealed interface QuoteResult permits QuoteResult.Priced, QuoteResult.Unavailable {
    String partnerCode();
    String serviceCode();

    record Priced(String partnerCode, String serviceCode, String locationKey,
                  BigDecimal chargeableWeightKg, List<ChargeLine> lines,
                  BigDecimal taxable, BigDecimal gst, BigDecimal total,
                  String rateCardVersion, String dataStatus) implements QuoteResult { }

    record Unavailable(String partnerCode, String serviceCode,
                       QuoteStatus status, String reason) implements QuoteResult { }
}

public enum QuoteStatus {
    NOT_SERVICEABLE, COD_NOT_AVAILABLE, WEIGHT_LIMIT_EXCEEDED, ZONE_UNKNOWN,
    RATE_UNAVAILABLE, CONFIG_INCOMPLETE, DISABLED, PARTNER_API_ERROR
}
```

### 7.2 Provider contract (evolves `DeliveryPartnerClient`)

```java
public interface RateProvider {
    String partnerCode();
    String serviceCode();
    QuoteResult quote(QuoteRequest request);   // never throws for business failures
}
```

`LiveApiProvider` wraps an existing `DeliveryPartnerClient` (adapter): maps `QuoteRequest`
-> `RequestParamRecord`, `RateResponse` -> `Priced`, and `PartnerApiException` -> `Unavailable(PARTNER_API_ERROR)`.

### 7.3 Template Method (your abstract class)

```java
public abstract class AbstractRateCardCalculator implements RateCardCalculator {

    private final List<EligibilityRule> eligibilityRules;
    private final ChargeEngine chargeEngine;

    protected AbstractRateCardCalculator(List<EligibilityRule> rules, ChargeEngine chargeEngine) {
        this.eligibilityRules = rules;
        this.chargeEngine = chargeEngine;
    }

    @Override
    public final QuoteResult quote(QuoteRequest req, RateCardSnapshot card) {
        for (EligibilityRule rule : eligibilityRules) {                 // Specification
            Optional<QuoteResult.Unavailable> fail = rule.check(req, card);
            if (fail.isPresent()) return fail.get();
        }
        Optional<String> location = resolveLocation(req, card);        // abstract
        if (location.isEmpty()) return card.unavailable(QuoteStatus.ZONE_UNKNOWN, "No zone/band for route");

        BigDecimal chargeable = chargeableUnits(req, card);              // hook, default below
        if (chargeable.compareTo(card.maxWeightKg()) > 0)
            return card.unavailable(QuoteStatus.WEIGHT_LIMIT_EXCEEDED, "Above " + card.maxWeightKg() + " kg");

        Optional<BigDecimal> freight = baseFreight(req, card, location.get(), chargeable); // abstract
        if (freight.isEmpty()) return card.unavailable(QuoteStatus.RATE_UNAVAILABLE, "No rate for " + location.get());

        PricingContext ctx = new PricingContext(req, card, location.get(), chargeable);
        ctx.add("BASE", applyDirection(req, card, freight.get()));
        chargeEngine.apply(ctx);                                         // ordered charge rules
        ctx.applyMinimumBill(card.minBill());
        return ctx.toPriced();                                           // GST added later, after margin
    }

    protected abstract Optional<String> resolveLocation(QuoteRequest req, RateCardSnapshot card);

    protected abstract Optional<BigDecimal> baseFreight(QuoteRequest req, RateCardSnapshot card,
                                                        String location, BigDecimal chargeable);

    /** Default: max(dead weight, volumetric weight, min chargeable). Hyperlocal overrides with km. */
    protected BigDecimal chargeableUnits(QuoteRequest req, RateCardSnapshot card) {
        BigDecimal vol = req.totalVolumeCm3()
                .divide(card.volumetricDivisor(), 3, RoundingMode.HALF_UP);
        return req.totalDeadWeightKg().max(vol).max(card.minChargeableKg());
    }

    protected BigDecimal applyDirection(QuoteRequest req, RateCardSnapshot card, BigDecimal forward) {
        return switch (req.direction()) {
            case FORWARD -> forward;
            case RTO -> card.rtoAmount(forward);      // SAME_AS_FORWARD / PERCENT / TABLE
            case REVERSE -> forward;                  // reverse uses its own slab rows (DIRECTION = REVERSE)
        };
    }
}
```

### 7.4 One pricing model

```java
@Component
public class ZoneSlabCalculator extends AbstractRateCardCalculator {

    private final ZoneResolver zoneResolver;

    public ZoneSlabCalculator(List<EligibilityRule> rules, ChargeEngine engine, ZoneResolver zoneResolver) {
        super(rules, engine);
        this.zoneResolver = zoneResolver;
    }

    @Override public PricingModel model() { return PricingModel.ZONE_SLAB; }

    @Override
    protected Optional<String> resolveLocation(QuoteRequest req, RateCardSnapshot card) {
        return zoneResolver.zoneFor(card.partnerId(), req.originPincode(), req.destinationPincode());
    }

    @Override
    protected Optional<BigDecimal> baseFreight(QuoteRequest req, RateCardSnapshot card,
                                               String zone, BigDecimal kg) {
        return card.slabFor(req.direction(), zone, kg).map(slab -> {
            BigDecimal extra = kg.subtract(slab.firstUnit());
            long addSlabs = extra.signum() <= 0 ? 0
                    : extra.divide(slab.addUnit(), 0, RoundingMode.CEILING).longValue();
            return slab.firstRate().add(slab.addRate().multiply(BigDecimal.valueOf(addSlabs)));
        });
    }
}
```

`DistanceBandCalculator` (India Post): `resolveLocation` returns the distance band.
`B2bFreightCalculator`: overrides `chargeableUnits` (CFT) and multiplies rate per kg.
`HyperlocalCalculator`: overrides `chargeableUnits` to return km, `resolveLocation` returns vehicle.

### 7.5 Charge engine (Strategy per calc type)

```java
public interface ChargeHandler {
    CalcType type();
    BigDecimal amount(ChargeRule rule, PricingContext ctx);   // ctx exposes base values and lines so far
}

@Component
public class ChargeEngine {
    private final Map<CalcType, ChargeHandler> handlers;

    public ChargeEngine(List<ChargeHandler> list) {
        this.handlers = list.stream().collect(Collectors.toUnmodifiableMap(ChargeHandler::type, h -> h));
    }

    public void apply(PricingContext ctx) {
        ctx.card().chargeRules().stream()
           .sorted(Comparator.comparingInt(ChargeRule::seq))
           .filter(rule -> rule.appliesTo(ctx))                 // APPLIES_WHEN + DIRECTIONS + tier
           .forEach(rule -> ctx.add(rule.chargeCode(),
                   handlers.get(rule.calcType()).amount(rule, ctx).setScale(2, RoundingMode.HALF_UP)));
    }
}
```

Example rows that cover the tricky cases:

| Partner | CHARGE_CODE | SEQ | CALC_TYPE | BASE | Effect |
|---|---|---|---|---|---|
| Blue Dart | ESS | 10 | PERCENT 25 | CODES `BASE,COD` | 25% on freight + COD |
| Blue Dart | FSC | 20 | PERCENT | CODES `BASE,ESS,ODA` | Fuel on top of ESS |
| Delhivery | DCS | 30 | FLAT | - | City surcharge, not in FSC base |
| Xpressbees | COD | 40 | TIERED FLAT, tier 0-2000 | COD_AMOUNT | Flat below Rs 2,000 |
| Xpressbees | COD | 40 | TIERED PERCENT, tier 2000+ | COD_AMOUNT | % above Rs 2,000 |
| Amazon | COD | 40 | PERCENT, COMBINE=MAX, FIXED_PART | COD_AMOUNT; DIRECTIONS `FORWARD,RTO` | Charged on returns too |

### 7.6 Eligibility rules (Specification)

```java
public interface EligibilityRule {
    Optional<QuoteResult.Unavailable> check(QuoteRequest req, RateCardSnapshot card);
}
```

Implement: `ServiceEnabledRule`, `ConfigCompleteRule`, `PickupServiceableRule`,
`DeliveryServiceableRule`, `ReversePickupRule`, `CodAllowedRule` (incl. max COD),
`ProhibitedItemRule`. Order them with `@Order`.

### 7.7 Margin + GST (fixes `MarkupPartnerClient`)

```java
// taxable = pre-tax total + margin;  gst = round(taxable * gstRate, 2);  total = taxable + gst
```

Margin % and GST % come from configuration (`rate.margin-percent`, `rate.gst-percent`),
not constants. Apply to rate-card and live-API results the same way.

### 7.8 Aggregation (parallel, nothing dropped)

```java
public List<QuoteResult> quoteAll(QuoteRequest req) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {   // Java 21
        List<Future<QuoteResult>> futures = providers.stream()
                .map(p -> executor.submit(() -> safeQuote(p, req)))
                .toList();
        return futures.stream().map(this::await).sorted(cheapestFirst()).toList();
    }
}
```

`safeQuote` converts unexpected failures into `Unavailable(PARTNER_API_ERROR)` with a
logged error ID. Live API calls keep their RestClient timeouts (already configured for UPS/DHL;
add for Delhivery/ShreeMurti).

### 7.9 Cache

`RateCardLoader.load(serviceId, date)` with `@Cacheable("rateCards")`; zone/pincode lookups
cached by `(partnerId, pin)`. Evict (`@CacheEvict(allEntries = true)`) when a rate card
version is activated.

---

## 8. Build order (one PR each)

| # | PR | Done when |
|---|---|---|
| 1 | Domain records, enums, `QuoteRequest` validation, chargeable-weight + rounding | Unit tests for volumetric weight, slab count, rounding |
| 2 | `V3` migration + entities + repositories | `@DataJpaTest` on Testcontainers PostgreSQL |
| 3 | `V4` sample seed (from the Excel) | App starts, rows present, all `DATA_STATUS = SAMPLE` |
| 4 | `ChargeEngine` + 4 handlers | Tests: ESS -> FSC order, tiered COD, COD on RTO, min/max |
| 5 | Eligibility rules + `ZoneSlabCalculator` + `RateCardProvider` | Golden tests (section 9) pass |
| 6 | `RateQuoteService` + `/api/v1/rates/quotes` + margin/GST + `LiveApiProvider` adapters | `@WebMvcTest`; every partner appears with a price or status |
| 7 | Caffeine cache + eviction | Second quote does no DB reads (verify with SQL log) |
| 8 | `DistanceBandCalculator`, `B2bFreightCalculator`, `HyperlocalCalculator` | Golden tests per model |
| 9 | Admin import of rate card / pincode / zone files (POI) | Upload creates a new card version, old one closed |

---

## 9. Golden tests (from `ShipOrbit_Rate_Calculator.xlsx`, sample data)

Input: 201301 -> 560001, FORWARD, COD Rs 1,499, declared Rs 1,499, 1 x 0.8 kg, 30x20x10 cm,
OWNER risk, margin 10%, GST 18%.

| Partner / service | Expected total (Rs) |
|---|---|
| Delhivery Surface 0.5kg | 219.36 |
| Delhivery Express 0.5kg | 275.18 |
| Delhivery Surface 5kg | 324.50 |
| Blue Dart Apex 0.5kg | 634.72 |
| Amazon Standard 0.5kg | 190.81 |
| Xpressbees Surface 2kg | 171.34 |
| Ekart 2kg Flat | 94.05 |
| India Post Speed Post | CONFIG_INCOMPLETE |

RTO case (201301 -> 781001, COD Rs 800, 1 x 0.4 kg, 10x10x10): Delhivery Surface 0.5kg = 80.48,
Ekart 2kg Flat = 0.00. Put these in a CSV and run with `@ParameterizedTest @CsvFileSource`.

Note: the sample Blue Dart row has no ESS. When ESS is added, its expected value changes.

---

## 10. Rules to keep

- Money: `BigDecimal`, `setScale(2, HALF_UP)` per charge line; totals = sum of rounded lines.
- A blank required config value -> `CONFIG_INCOMPLETE`, never treated as 0.
- New partner on an existing pricing model = data only (rows), no code.
- New charge type = row; new calc type = one `ChargeHandler` class.
- New pricing model = one `AbstractRateCardCalculator` subclass.
- Partner subclass only for logic config cannot express. Document why in the class.
- No DB transaction open while calling live partner APIs.
- Tests never call live partner APIs (MockWebServer/WireMock for adapters).
