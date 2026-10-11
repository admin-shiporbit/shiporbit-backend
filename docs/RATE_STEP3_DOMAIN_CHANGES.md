# Rate Calculator, Step 3: Domain Model, Validation, Weight and Slab Maths (11 Oct 2026)

Branch: `rate-calculator`. Nothing committed.

**Status:**
- The weight and slab maths (`ChargeableWeight`, `SlabFreight`) were compiled with plain `javac` and checked against the golden values: **17/17 pass** (details below).
- The Spring-dependent files (validation, controller, service) were **not compiled**, because Gradle can't reach Maven Central from my environment. Rebuild before testing.

All Java paths are under `src/main/java/com/shiporbit/backend/`.

---

## Summary

| # | File | Action |
|---|---|---|
| 1 | `rate/core/QuoteRequest.java` | Added |
| 2 | `rate/core/ShipmentPackage.java` | Added |
| 3 | `rate/core/B2bOptions.java` | Added |
| 4 | `rate/core/HyperlocalOptions.java` | Added |
| 5 | `rate/core/RiskType.java`, `AddOn.java`, `QuoteStatus.java` | Added (enums) |
| 6 | `rate/core/ChargeLine.java`, `SlabInfo.java`, `QuoteResult.java` | Added (result model) |
| 7 | `rate/core/WeightRules.java`, `ChargeableWeight.java` | Added (weight maths) |
| 8 | `rate/core/SlabFreight.java` | Added (slab maths) |
| 9 | `rate/dto/response/QuotePreview.java` | Added |
| 10 | `rate/service/QuotePreviewService.java` | Added |
| 11 | `rate/api/RateQuoteController.java` | Added |
| 12 | `exception/GlobalExceptionHandler.java` | Edited (2 handlers added) |

**Why a new `rate/core` package:** `RATE_CALCULATOR_GUIDE.md` §5 says the domain model (request, results, maths) lives in `core`, separate from JPA (`entity`) and HTTP (`api`). Classes here have no Spring or database code, so they're plain, unit-testable Java.

**Enums not moved:** `PaymentMode`, `ShipmentDirection`, `ShipmentType`, `MpsRule` and `Rounding` stay in `rate/entity`, and `core` imports them. Moving them would touch every entity import without a compiler to check. They're plain enums, so the dependency is harmless; they can move later in one IDE refactor.

---

## 1. `QuoteRequest.java`

**Why:** the single input for pricing every partner. It mirrors `docs/samples/quote_request_*.json`.

**Fields:** `originPincode`, `destinationPincode`, `direction` (FORWARD/RTO/REVERSE), `paymentMode` (COD/PREPAID), `codAmount`, `declaredValue`, `riskType`, `productCategory`, `isDocument`, `pickupDate`, `packages`, `addOns`, `partners`, `b2b`, `hyperlocal`.

**Validation (Bean Validation, triggered by `@Valid` in the controller):**
- Pincodes: exactly 6 digits.
- `direction`, `paymentMode`, `declaredValue` are required. Amounts must be ≥ 0, with at most 2 decimals.
- `packages`: 1 to 100 entries, each validated (see `ShipmentPackage`).
- **Cross-field rule** `isCodAmountConsistent()`: COD requires `codAmount > 0`; PREPAID requires `codAmount = 0`. It's reported as field `codAmountConsistent`.

**Defaults** (compact constructor; calculators never see null):

| Field | Default |
|---|---|
| `codAmount` | 0 |
| `riskType` | OWNER |
| `productCategory` | GENERAL (trimmed, upper-cased so it matches `APPLIES_VALUE`) |
| `pickupDate` | today in Asia/Kolkata (decides which rate card and which dated rules apply) |
| `addOns`, `partners` | empty sets (empty `partners` = all partners; names upper-cased) |
| `b2b` | 1 LR, no appointment, no floor delivery |
| `hyperlocal` | no distance, no vehicle |

**`isDocument`:** the Java component is named `document`, with `@JsonProperty("isDocument")`. The JSON key stays as in the samples, and this avoids Jackson treating the `is` prefix specially.

**Helpers:** `isCod()`, `shipmentType()` (DOCUMENT/PARCEL; used for slab selection, gap 2), `isSameCityPincode()`, `totalPieces()`, `includesPartner(name)`. The `is*` helpers are `@JsonIgnore`, so they don't show up as extra JSON fields in the preview response.

**Not validated (deliberately):**
- `pickupDate` in the past is allowed, so you can re-quote fixed dates such as the golden 2026-10-12.
- `hyperlocal.distanceKm` missing is not a request error. Only hyperlocal services need it, and they report `DISTANCE_REQUIRED` (step 4).

## 2. `ShipmentPackage.java`

**Why:** one line per set of identical boxes: `count`, `deadWeightKg`, `lengthCm`, `widthCm`, `heightCm`.
- Named `ShipmentPackage`, not `Package`, because `java.lang.Package` is auto-imported and would clash.
- Validation: `count` 1 to 1000; weight and dimensions required, > 0, with limited decimals.
- Helpers: `volumeCm3()` (one box), `totalDeadWeightKg()`, `totalVolumeCm3()`.

## 3. `B2bOptions.java`, 4. `HyperlocalOptions.java`

**Why:** keep model-specific inputs out of the main request.
- **`B2bOptions`:** `numberOfLRs` (default 1, maximum 100), `appointmentDelivery`, `floorDelivery`. These are used by B2B charge rules later.
- **`HyperlocalOptions`:** `distanceKm` (> 0) and `vehicle`. `vehicle` is upper-cased because it must equal the hyperlocal slab `LOCATION_KEY` (e.g. `BIKE`). It's a `String`, not an enum, so a new vehicle type is a data change only.

## 5. Enums: `RiskType`, `AddOn`, `QuoteStatus`

- `RiskType` OWNER/CARRIER maps to the `RISK_OWNER`/`RISK_CARRIER` charge rules.
- `AddOn` OTP/QC/FRAGILE maps to the `ADDON_*` rules.
- `QuoteStatus` is the full status list from the design: OK, VALIDATION_ERROR, DISABLED, CONFIG_INCOMPLETE, ZONE_UNKNOWN, NOT_SERVICEABLE, COD_NOT_AVAILABLE, WEIGHT_LIMIT_EXCEEDED, RATE_UNAVAILABLE, DISTANCE_REQUIRED, PARTNER_API_ERROR.

## 6. Result model: `ChargeLine`, `SlabInfo`, `QuoteResult`

**Why:** the calculators (step 4) return these. The API (step 5) maps them to JSON.
- **`ChargeLine(code, amount)`:** one breakdown line, always rounded to 2 dp HALF_UP. `ChargeLine.BASE = "BASE"` is the freight line code.
- **`SlabInfo`:** how freight was derived (first unit and rate, additional unit, steps and rate). This is what the sample response shows under `slab`.
- **`QuoteResult`:** a **sealed** interface with exactly 2 cases:
  - `Priced`: zone, volumetric/chargeable weight, slab, breakdown, taxable, GST, total. Status is always OK.
  - `Unavailable`: status plus reason. The constructor rejects status OK.

  Sealed means a `switch` over results must handle both cases, so the compiler catches a missing branch.
- Partners are identified by `partnerId` and `partnerName`, the same as the `/lane` API.

## 7. `WeightRules` and `ChargeableWeight`

**Why:** chargeable weight is the first number every courier and B2B price depends on. It's a pure function, so it's easy to test.
- **`WeightRules`:** the card settings that matter for weight: `volumetricDivisor`, `cftKg`, `minChargeableWeightKg` and `mpsRule`. It rejects divisor or CFT ≤ 0, since that would cause a division error.
- **`ChargeableWeight.of(packages, rules)`:** returns dead, volumetric and chargeable weight, in kg to 3 dp HALF_UP.
  - **Courier volumetric:** L×W×H / divisor.
  - **B2B volumetric:** L×W×H × CFT / 28,316.846592 (cm³ per cubic foot). It multiplies before dividing, to avoid rounding twice.
  - **MPS TOTAL:** max(total dead, total volumetric). **PER_PIECE:** the sum of max(dead, volumetric) for each box.
  - The card minimum is then applied: `max(billed, MIN_CHARGEABLE_WEIGHT_KG)`.
  - If the divisor is set it's used; otherwise CFT; with neither, dead weight only.

## 8. `SlabFreight`

**Why:** base freight from one slab row. This also implements gap 5 (`ROUNDING`).
- If `x ≤ FIRST_UNIT` or `ADD_UNIT = 0`: `FIRST_RATE`.
- **CEIL_STEP:** `FIRST_RATE + CEIL((x − FIRST_UNIT) / ADD_UNIT) × ADD_RATE`.
- **EXACT:** the same without the ceiling (steps kept to 6 dp).
- The amount is rounded to 2 dp HALF_UP. `additionalSteps` is returned for `SlabInfo`.

### Verification (plain javac, golden values)

| Case | Result |
|---|---|
| Courier 1 × 0.8 kg, 30×20×10, divisor 5000 | volumetric 1.200, chargeable 1.200 ✅ |
| Amazon slab 0.5 @ ₹44 + 0.5 @ ₹40 at 1.2 kg | BASE 124.00, 2 steps ✅ (golden 124.0) |
| Rivigo CFT 6, minimum 20 | volumetric 1.271 ✅ (golden), chargeable 20 ✅ |
| Rivigo ₹11.5/kg, EXACT, 20 kg | 230.00 ✅ (golden 230.0) |
| 20.3 kg EXACT vs CEIL_STEP | 233.45 vs 241.50 ✅ |
| Ekart 2kg flat at 1.2 kg | 72.45 ✅ |
| x = first unit / x just above it | 44.00 / 84.00 ✅ |
| MPS TOTAL vs PER_PIECE (5 kg small box + 2 × 0.5 kg large boxes) | 24.2 vs 29 ✅ |
| Dead weight only / minimum 2 kg | 0.8 / 2 ✅ |

## 9–11. Preview API: `QuotePreview`, `QuotePreviewService`, `RateQuoteController`

**Why:** you wanted to check each step through a controller rather than tests. `POST /api/v1/rates/quotes/preview` exercises all of step 3 against the real database:
1. `@Valid` checks the request. Errors come back as 400 with field messages.
2. It returns the **normalised request** (defaults filled in), `shipmentType` and `totalPieces`.
3. For each enabled service (filtered by `partners` if given), it loads the ACTIVE card for `pickupDate` and computes dead, volumetric and chargeable weight. It also shows `maxWeightKg` and `withinWeightLimit`.

- **No prices yet.** Pricing comes in step 4 (`ChargeEngine`, calculators, eligibility). Step 5 adds `POST /api/v1/rates/quotes` to this same controller.
- **Queries:** one card query per service (about 21). Fine for a preview; the real quote path will use the Caffeine cache.
- **Hyperlocal services:** their weight is shown but not used. They price by km.

**Try:**
```bash
curl -s -X POST localhost:8080/api/v1/rates/quotes/preview \
  -H "Content-Type: application/json" \
  -d @docs/samples/quote_request_intercity.json | jq
```
**Expected (golden request):**
- Courier services: volumetric 1.200, chargeable 1.200.
- B2B (Rivigo, TCI, Safexpress): volumetric 1.271, chargeable 20.000.
- India Post: depends on its card.
- All within weight limit.

**Validation error example:**
```bash
curl -s -X POST localhost:8080/api/v1/rates/quotes/preview -H "Content-Type: application/json" \
  -d '{"originPincode":"2013","destinationPincode":"560001","direction":"FORWARD","paymentMode":"COD","codAmount":0,"declaredValue":100,"packages":[]}' | jq
```
This returns 400 with messages for `originPincode`, `packages` and `codAmountConsistent`.

## 12. `GlobalExceptionHandler.java` (edited)

**Why:** two kinds of bad input were returning 500 instead of 400.

| Added handler | When | Before |
|---|---|---|
| `HttpMessageNotReadableException` → 400 "Malformed Request" | Broken JSON or an unknown enum value, e.g. `"paymentMode":"CASH"` | 500 via the catch-all |
| `HandlerMethodValidationException` → 400 "Validation Failed" | `@Validated` request parameters, e.g. `/lane?origin=12` | 500 via the catch-all |

The existing handlers are unchanged. The new ones follow the same `ErrorResponse` format.

---

## Not done in this step (deliberately)

- **JUnit tests:** not added, per your earlier preference to verify through controllers. The 17 checks above are ready to turn into `ChargeableWeightTest` and `SlabFreightTest` when you want them.
- **Slab selection** (most-specific `PAYMENT_MODE`/`SHIPMENT_TYPE` row), charge rules, eligibility, margin and GST: step 4.

## Next: step 4

`ChargeEngine` plus handlers (FLAT / PERCENT / PER_UNIT, tiers, validity dates, categories), eligibility rules (pincode, COD, weight limit, zone), `ZoneSlabCalculator`, `RateCardProvider`, then golden checks.
