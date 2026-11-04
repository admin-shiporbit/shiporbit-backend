# Rate Calculator, Step 4: Pricing Engine and Quote API (11 Oct 2026)

Branch: `rate-calculator`. Nothing committed.

## Status

- **Golden check: 42/42 match.** Both sample requests (intercity 201301→560001 and same-city 201301→201301 with 12 km BIKE) were run through the engine against V1–V10 seed data on PostgreSQL 16. Every service's status, zone, chargeable weight, each breakdown amount, taxable amount and total equals `docs/samples/quote_response_*.json`.
- **17 extra scenarios checked by hand** (see §8): PREPAID slab, document slab, peak dates, category surcharge, weight tiers, EXACT rounding, RTO, REVERSE, weight limit, COD not available, unknown pincode.
- **What was compiled and run:** the engine, `core` and `RateQuoteService`, with plain `javac` using stand-in annotations, and a stand-in loader for `RateQuoteService`.
- **Not compiled:** `RateCardLoader` (JPA), the repository additions and the controller, because Gradle can't reach Maven Central from here. **Rebuild and run the curl in §9.**

**Scope change vs. the guide:** the guide split this work over steps 4–5. Because all four pricing models share the same slab mechanism, this step prices **every model** (courier, India Post, B2B, hyperlocal), and includes margin/GST and the `POST /api/v1/rates/quotes` endpoint. Still to do: live partner API adapters, caching, and the remaining items in §10.

All Java paths are under `src/main/java/com/shiporbit/backend/rate/`.

---

## Summary

| # | File | Action | Pattern |
|---|---|---|---|
| 1 | `engine/ServiceInfo`, `SlabRow`, `ChargeRule`, `RateCardSnapshot`, `PincodeInfo`, `PartnerLane`, `PricingContext`, `Rejection` | Added | Immutable value objects |
| 2 | `engine/SlabSelector` | Added | |
| 3 | `engine/charge/ChargeHandler` + `Flat`/`Percent`/`PerUnitChargeHandler` | Added | Strategy |
| 4 | `engine/charge/ChargeEngine` | Added | Ordered rule engine |
| 5 | `engine/rule/EligibilityRule` + 5 rules | Added | Specification |
| 6 | `engine/MarginTaxPolicy` | Added | |
| 7 | `engine/RateCardCalculator`, `AbstractRateCardCalculator` | Added | Template Method |
| 8 | `engine/ZoneMapCalculator`, `HyperlocalCalculator` | Added | |
| 9 | `engine/CalculatorRegistry` | Added | Registry |
| 10 | `service/RateCardLoader` | Added | |
| 11 | `service/RateQuoteService` | Added | |
| 12 | `dto/response/QuoteResponse` | Added | |
| 13 | `api/RateQuoteController` | Edited (new `POST /api/v1/rates/quotes`) | |
| 14 | `repository/RateCardRepository`, `RateSlabRepository`, `RateChargeRuleRepository` | Edited (bulk queries) | |
| 15 | `core/SlabFreight` | Edited (number format) | |
| 16 | `core/QuoteResult` | Edited (comment only) | |

---

## 1. Engine value objects

**Why:** the engine works on immutable copies of the database rows, not JPA entities. This has three benefits:
- A snapshot can be cached (step 6) and shared across threads. Entities are mutable and tied to a database session.
- The engine has no JPA or Spring dependencies, so it can be tested and run with plain Java. That's how the golden check ran.
- Lazy loading can never trigger during pricing.

| Record | What it holds |
|---|---|
| `ServiceInfo` | service id, partner id and name, service code, pricing model, mode |
| `SlabRow` | one slab row, plus `covers(x)` (FROM < x ≤ TO) and `specificity()` (how many of PAYMENT_MODE/SHIPMENT_TYPE are set) |
| `ChargeRule` | one charge rule, plus `validOn(date)`, `appliesTo(direction)`, `inTier(value)` |
| `RateCardSnapshot` | the card settings, `WeightRules`, slabs, and rules **sorted by SEQ** |
| `PincodeInfo` | serviceability flags at one pincode |
| `PartnerLane` | a partner's origin and destination flags plus `LOCATION_KEY` per pricing model; `null` = no row |
| `PricingContext` | request + card + lane + chargeable weight, which the rules and engine read |
| `Rejection` | status + reason |

## 2. `SlabSelector`

Picks the slab by direction, `LOCATION_KEY` and FROM < x ≤ TO. A row matches when `PAYMENT_MODE`/`SHIPMENT_TYPE` is null (any) or equal to the request. **The most specific match wins**, which is what makes V10 gaps 1 and 2 work: Ekart prepaid gets ₹67.45 rather than ₹72.45, and a DTDC document gets the document rate.

## 3. Charge handlers (Strategy)

One class per `CALC_TYPE`. Each returns the raw amount; the engine does the clamping and rounding.
- `FLAT` → `FIXED_AMOUNT`
- `PERCENT` → `FIXED_AMOUNT + RATE_VALUE% × base`
- `PER_UNIT` → `RATE_VALUE × chargeable units` (kg, or km for hyperlocal)

A new calculation type means a new handler class only. `ChargeEngine` fails at startup if any `CalcType` has no handler.

## 4. `ChargeEngine`

Starts with the `BASE` line, then applies each rule **in SEQ order**. A rule applies only if all of these hold:
1. `validOn(pickupDate)` (gap 4: peak surcharge dates).
2. `appliesTo(direction)` (e.g. COD rules are FORWARD only, so there's no COD on RTO).
3. Its `APPLIES_WHEN` condition holds. This is an exhaustive `switch`, so the compiler flags any new enum value:

   | Condition | True when |
   |---|---|
   | `ALWAYS` | always |
   | `COD` / `PREPAID` | payment mode |
   | `ODA_DESTINATION` / `ODA_PICKUP` | pincode ODA flag |
   | `SPECIAL_CITY` | destination flag |
   | `RISK_OWNER` / `RISK_CARRIER` | `riskType` |
   | `ADDON_*` | `addOns` contains it |
   | `CATEGORY` | `APPLIES_VALUE` equals `productCategory`, ignoring case (gap 6) |

4. The base value is inside the tier (`[TIER_FROM, TIER_TO)`). The base value is:

   | Base | Value |
   |---|---|
   | `BASE_FREIGHT` | the freight |
   | `CHARGE_CODES` | sum of the listed lines **computed so far**, e.g. `BASE,ODA` |
   | `COD_AMOUNT` | COD amount |
   | `DECLARED_VALUE` | declared value |
   | `CHARGEABLE_WEIGHT` | chargeable kg (gap 3) |
   | `NONE` | 0 |

The amount is then clamped to `MIN_AMOUNT`/`MAX_AMOUNT` and rounded to 2 dp HALF_UP.
- **Same code twice** (tiers such as Rivigo `HEAVY_HANDLING` seq 65/66): the amounts add into one line.
- **Zero lines** are kept in the engine, because a later `CHARGE_CODES` base may reference them. The API response hides them.

## 5. Eligibility rules (Specification)

Each rule returns either "eligible" or a `Rejection(status, reason)`. The first failure wins. **Order** (it matters, see the PikNDel note below):
1. `PincodeServiceabilityRule`: the origin row must exist with `PICKUP` (`REVERSE_PICKUP` for REVERSE); the destination row must exist with `DELIVERY`. Fails with `NOT_SERVICEABLE`.
2. `PaymentModeRule`: COD needs destination `COD`, otherwise `COD_NOT_AVAILABLE`. Prepaid needs `PREPAID`, otherwise `NOT_SERVICEABLE`.
3. Model rules. Hyperlocal only:
   - `SameCityRule`: same `CITY_CODE`, otherwise `NOT_SERVICEABLE`.
   - `HyperlocalDistanceRule`: distance required (`DISTANCE_REQUIRED`), vehicle required (`VALIDATION_ERROR`), and distance ≤ `MAX_DISTANCE_KM` (`NOT_SERVICEABLE`).
4. `WeightLimitRule`: chargeable weight ≤ `MAX_WEIGHT_KG`, otherwise `WEIGHT_LIMIT_EXCEEDED`.

**Why this order:** PikNDel has no row for 560001. With the distance check first, it reported `DISTANCE_REQUIRED`; the golden expects `NOT_SERVICEABLE`. Lane checks now run first.

## 6. `MarginTaxPolicy`

- MARGIN = pre-tax subtotal × margin %.
- GST = (subtotal + MARGIN) × GST %.
- Each is rounded to 2 dp HALF_UP. Both are added as breakdown lines; taxable = subtotal + MARGIN, and total = taxable + GST.
- The percentages come from config **with defaults in code**, so no YAML change is needed. To override:
  ```yaml
  shiporbit:
    rates:
      margin-percent: 10
      gst-percent: 18
  ```

## 7. Calculators (Template Method + Registry)

**`AbstractRateCardCalculator.quote()`** is `final`, so the pipeline can't be reordered by a subclass:
1. Card checks: `RATES_INCLUDE_GST = true` → `CONFIG_INCOMPLETE` (not supported yet; no sample card uses it). No slabs → `CONFIG_INCOMPLETE`.
2. Chargeable weight (`ChargeableWeight`, step 3).
3. Eligibility (§5).
4. Location: `resolveLocation()`, with `ZONE_UNKNOWN` if missing. RTO on a card with `RTO_RULE = NOT_APPLICABLE` → `RATE_UNAVAILABLE`.
5. Slab: `SlabSelector`, with FORWARD slabs for FORWARD and RTO, and REVERSE slabs for REVERSE. No slab → `RATE_UNAVAILABLE`.
6. Freight: `SlabFreight` (CEIL_STEP or EXACT, gap 5). RTO = forward freight × `RTO_PERCENT`.
7. Charges (`ChargeEngine`), then `MIN_BILL` (a `MIN_BILL_TOPUP` line if the subtotal is below it), then margin and GST.

**Subclasses only supply what differs:**

| Calculator | Models | Location | Units | Extra rules |
|---|---|---|---|---|
| `ZoneMapCalculator` | ZONE_SLAB, DISTANCE_BAND, B2B_FREIGHT | `SO_ZONE_MAP` for the model | chargeable kg | none |
| `HyperlocalCalculator` | HYPERLOCAL | `hyperlocal.vehicle` (e.g. BIKE) | `distanceKm` | same city, distance |

**Why one calculator for three models:** courier, India Post and B2B differ only in data (zone names vs. distance bands vs. zone pairs; divisor vs. CFT). Separate classes would duplicate code. A model that really behaves differently gets its own subclass.

**`CalculatorRegistry`** maps pricing model to calculator from all calculator beans. It fails at startup if two calculators claim the same model. A model with no calculator returns `CONFIG_INCOMPLETE` instead of crashing.

## 8. Extra scenarios (checked by hand against the seed data)

| Case | Result |
|---|---|
| Ekart 2kg, COD vs PREPAID (gap 1) | BASE 72.45 vs **67.45** |
| DTDC parcel vs document (gap 2) | slab 46 vs **36**, total 261.94 vs 208.20 |
| Delhivery, 12 Oct vs 1 Nov (gap 4) | no peak vs **PEAK_SURCHARGE 15** |
| Bluedart GENERAL vs ELECTRONICS (gap 6) | **ELECTRONICS_SURCHARGE 50** (minimum) |
| Rivigo 150.3 kg (gaps 3+5) | BASE 1728.45 (exact kg), HEAVY_HANDLING 250 |
| Rivigo 600 kg (gap 3) | HEAVY_HANDLING 300 (0.50 × 600) |
| Delhivery RTO | freight 100% of forward, no COD (COD is FORWARD only) |
| Delhivery REVERSE | REVERSE slab (54.00 first rate) |
| India Post REVERSE | NOT_SERVICEABLE (no reverse pickup) |
| Delhivery 6 kg | WEIGHT_LIMIT_EXCEEDED (max 5) |
| COD to Leh / Port Blair | COD_NOT_AVAILABLE |
| Unknown pincode 999999 | NOT_SERVICEABLE |

## 9. Spring layer

### 10. `RateCardLoader`

Turns database rows into snapshots with a **fixed number of queries**, whatever the number of services:
- `servicesOn(date)`: partners, ACTIVE cards on that date, slabs of those cards, rules of those cards, enabled services (5 queries).
- `lanes(origin, destination)`: pincode rows and zone rows for every partner (2 queries).

A service with no ACTIVE card on the date gets `card = null`, and `RateQuoteService` reports it as `RATE_UNAVAILABLE`.

### 11. `RateQuoteService`

1. Load services (filtered by `partners` names if given) and the lanes.
2. Price each service with the calculator for its model.
3. Build the response:
   - Group by partner, in partner id order.
   - Mark `cheapestForPartner` and the overall `cheapest`.
   - Hide zero charge lines (BASE is always shown).
   - Set `dataStatus` to SAMPLE if any card used is SAMPLE.
   - Generate a `quoteId` like `Q-20261012-6FD7E9F9`.

It doesn't catch exceptions on purpose: business failures are `Unavailable` results, and anything else is a bug that should show as a 500.

### 12. `QuoteResponse`

Same shape as the sample JSON: `quoteId`, `currency`, `pickupDate`, `dataStatus`, `cheapest`, and `results[]` (partner → `services[]`). An unpriced service shows only `service`, `mode`, `dataStatus`, `status` and `reason`, because nulls are omitted.

**Differences from the samples:**
- Charge codes are the rate-card codes: `FOV_OWNER` where the sample shows `FOV`/`ROV`, and `COD` where it shows `COD_DOD`. The amounts are identical. Display labels can be added later if you want them.
- The `slab` object has one shape for all models: `unit`, `firstUnit`, `firstRate`, `additionalUnit`, `additionalSteps`, `additionalRate`.
- Hyperlocal `zone` shows the vehicle, e.g. `BIKE`.

### 13. `RateQuoteController` (edited)

Added `POST /api/v1/rates/quotes` with a `@Valid` body. `/preview` from step 3 is unchanged.

### 14. Repositories (edited)

- **`RateCardRepository.findAllActiveOn(date)`:** every ACTIVE card valid on the date, in one query.
- **`RateSlabRepository.findByRateCardIdIn(ids)` and `RateChargeRuleRepository.findByRateCardIdIn(ids)`:** all rows for the loaded cards, in one query each.

### 15. `core/SlabFreight` (edited)

EXACT steps were printed as `6E+2`. They now print as `600` (trailing zeros dropped without switching to exponent form).

### 16. `core/QuoteResult` (comment only)

`chargeableWeightKg` is always in kg. Hyperlocal's km figure is in `slab.unit` and `slab.additionalSteps`.

---

## Try it

```bash
# All partners, golden intercity request
curl -s -X POST localhost:8080/api/v1/rates/quotes -H "Content-Type: application/json" \
  -d @docs/samples/quote_request_intercity.json | jq '.cheapest, (.results[] | {partner, services: [.services[] | {service, status, total}]})'

# Same city, 12 km bike (hyperlocal partners priced)
curl -s -X POST localhost:8080/api/v1/rates/quotes -H "Content-Type: application/json" \
  -d @docs/samples/quote_request_samecity.json | jq '.results[] | select(.partner=="BORZO")'

# Only two partners, prepaid
curl -s -X POST localhost:8080/api/v1/rates/quotes -H "Content-Type: application/json" -d '{
  "originPincode":"201301","destinationPincode":"560001","direction":"FORWARD","paymentMode":"PREPAID",
  "declaredValue":1499,"pickupDate":"2026-10-12",
  "packages":[{"count":1,"deadWeightKg":0.8,"lengthCm":30,"widthCm":20,"heightCm":10}],
  "partners":["EKART","DELHIVERY"]}' | jq
```

**Expected (intercity):**
- `cheapest` = EKART 2KG_FLAT ₹94.05.
- DELHIVERY SURFACE_05KG ₹219.36, RIVIGO ₹797.62.
- The 3 hyperlocal services are NOT_SERVICEABLE.

**Expected (same city):** BORZO ₹201.19, PIKNDEL ₹185.61, SHADOWFAX HYPERLOCAL_BIKE ₹160.95.

## 10. Next

- **Step 5:**
  - Live partner API adapters (Delhivery, ShreeMurti, UPS, DHL) as a separate provider, run in parallel with the rate-card results.
  - Partner failures become `PARTNER_API_ERROR` instead of being dropped.
  - Retire `/api/rate/aggregator` once the frontend moves.
- **Step 6:**
  - Caffeine cache on `servicesOn(date)`, cleared when a card is activated.
  - Amazon rate-card importer.
  - JUnit tests from the golden and scenario checks, whenever you want them.
