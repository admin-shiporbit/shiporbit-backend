# Change Log

Running log of changes made to this codebase, in reverse-chronological order.
Each entry: what was wrong, why the fix is the chosen approach, and exactly which files changed.

---

## 2026-09-05 — Rate calculator wired up: zone lookup, REST endpoint, unit tests

**What**

Followed up the Strategy-pattern refactor above by making it actually reachable and by
covering the ported math with tests (couldn't run a build in this environment, so tests
are the substitute verification).

- `Zones.fromState(String)` - new static helper, case-insensitive exact match against the
  state/UT names already on each zone. Throws `IllegalArgumentException` (already mapped
  to 400) for an unmapped state. Needed because `ShipmentRequest` requires a `Zones` value,
  but an API caller only knows a state/city name.
- `RateCalculator.PartnerInfo` (code, name) + `getAvailablePartners()` - lets a caller list
  registered partners without reaching into strategy internals.
- "Not serviceable" now throws a dedicated `PartnerNotServiceableException` (422 via
  `GlobalExceptionHandler`) instead of a generic `IllegalStateException` (which would have
  fallen through to the catch-all 500 handler).
- `RateController` (`/api/v1/rates`) - `GET /partners`, `POST /quote/{partnerCode}` (single
  partner), `POST /quotes` (compare every serviceable partner) - backed by `RateService` /
  `RateServiceImpl`, which maps the public `RateQuoteRequest` record to the internal
  `ShipmentRequest` and `RateBreakdown` back to `RateQuoteResponse`. Followed the existing
  `PickUpAddressController` / `PickupService` conventions (constructor injection, `@Valid`
  request records with jakarta.validation annotations in `dto`, service interface + impl).
- Unit tests for the Delhivery strategy: `ZonesTest`, `DelhiveryRateSourceTest` (zone
  matrix + override precedence + case-insensitivity), `DelhiveryRateStrategyTest` (full
  breakdown hand-verified against the ported formulas - see the Warangal example: 48kg
  volumetric weight, Rs.13.5/kg, working out to a subtotal of 1977.6 and a rounded total
  of 1978 - plus the Sikkim override, and conditional-charge on/off behavior). No Spring
  context needed - these instantiate `DelhiveryRateSource`/`DelhiveryRateCardConfig`
  directly, so they run fast and don't depend on the DB/security setup.

**Files changed**

- New: `rate/RateCalculator.PartnerInfo` (added to existing `RateCalculator.java`),
  `exception/PartnerNotServiceableException.java`,
  `dto/DeliveryPartnerResponse.java`, `dto/RateQuoteRequest.java`, `dto/RateQuoteResponse.java`,
  `service/RateService.java`, `service/RateServiceImpl.java`, `controller/RateController.java`
- Modified: `rate/Zones.java` (added `fromState`), `rate/RateCalculator.java` (added
  `PartnerInfo`/`getAvailablePartners`, switched to `PartnerNotServiceableException`),
  `exception/GlobalExceptionHandler.java` (added the 422 handler)
- New tests: `test/.../rate/ZonesTest.java`, `test/.../rate/delhivery/DelhiveryRateSourceTest.java`,
  `test/.../rate/delhivery/DelhiveryRateStrategyTest.java`

**Not verified**

Same limitation as before - no JDK 17 / no network for the Gradle distribution here, so
none of this (including the new tests) has actually been compiled or run. Please run
`./gradlew test` on your machine; the hand-worked-out numbers in
`DelhiveryRateStrategyTest` are in the test's comments so a failure is easy to trace back
to a wrong assumption on my part.

**Still open**

A second delivery partner needs its actual rate card (zone matrix, FSC/ROV/handling/ODA
rules, etc.) before it can be encoded the same way Delhivery was - that's business data
only you have.

**Follow-up (same day) - fixed while smoke-testing**

`POST /api/v1/rates/quote/DELHIVERY` 500'd with
`Cannot map 'null' into type 'boolean'` on `RateQuoteRequest["floorDelivery"]` when the six
delivery-flag fields were left out of the request body. Records deserialize via their
canonical constructor, and Jackson can't map a missing JSON property into a primitive
`boolean` there. Changed `floorDelivery`/`mallDelivery`/`csdArmyDelivery`/
`sundayOrHolidayDelivery`/`toPay`/`chequePayment` on `RateQuoteRequest` from `boolean` to
boxed `Boolean`, and `RateServiceImpl.toShipmentRequest` now reads them via
`Boolean.TRUE.equals(...)` so a missing/null flag means false, same as before. Not touched:
`AddressRequest.isDefault` has the same primitive-boolean shape and would hit the same
error if omitted - pre-existing, unrelated to this feature, flagging in case it bites later.

---

## 2026-09-05 — ODA disabled by default (was charging on every quote)

**Problem**

Every rate quote was including an ODA (out-of-delivery-area) charge, even for
ordinary, fully-serviceable zones/pincodes. `computeOda()`'s exemption check
(`config.deliveryOdaExemptPincodes.contains(pincode)`) reads an empty `HashSet` -
nobody has ever populated it - so no pincode can ever be "exempt," meaning
`odaMode=1` (destination-pincode based) applied ODA to literally every shipment
regardless of zone serviceability. Zone-serviceability and ODA-exemption were never
related in this design; ODA is opt-out (charge by default unless the pincode is on a
separate exempt list), so an empty exempt list is the same as ODA-on-everything, not
ODA-on-nothing.

This is the same behavior `RateCalculator_old` had (its `pickupOdaExemptPincodes`/
`deliveryOdaExemptPincodes` were also empty `HashSet`s in `defaultSmeConfig()`), and it
lines up with a `rate_oda_exempt_pincode_delhivery` table that already exists in
`schema.sql` (with a `PICKUP`/`DELIVERY` `exempt_type` column) but was never wired up to
anything - so the exempt list has always been empty, just nobody had hit ODA charges
in practice until now.

**Why this fix**

Confirmed with the user: until the real ODA-exempt pincode list exists (from Delhivery's
contract) or that list is wired to the DB table already scaffolded for it, ODA should be
0, not charged-on-everything. Rather than deleting the ODA calculation, added
`RateCardConfig.odaEnabled` (default `false`) and gated `computeOda()` behind it - so the
existing slab logic, `odaMode`, and exempt-pincode plumbing are all still there and ready
to flip on the moment real pincode data exists; nothing needs to be rewritten, just
`odaEnabled = true` plus populating the exempt sets (or wiring the DB table).

**Files changed**

- `rate/RateCardConfig.java` - added `public boolean odaEnabled = false` with a comment
  explaining the opt-out-not-opt-in semantics that make an empty exempt set dangerous.
- `rate/delhivery/DelhiveryRateStrategy.java` - `computeOda()` now returns 0 immediately
  when `!config.odaEnabled`.
- `rate/delhivery/DelhiveryRateCardConfig.java` - added a comment pointing at
  `odaEnabled` for when this gets real pincode data.
- `test/.../rate/delhivery/DelhiveryRateStrategyTest.java` - updated the hand-verified
  breakdown (oda 750 -> 0, subtotal 1977.6 -> 1227.6, total 1978 -> 1228) and added
  `odaIsZeroUntilEnabledWithRealExemptPincodeData` covering a shipment that would have
  been a "worst case" ODA hit (heavy weight, obviously-not-in-the-empty-set pincode) to
  lock in that it still charges nothing while disabled.

**Still open**

Same as before: real ODA-exempt pincode data (or wiring `rate_oda_exempt_pincode_delhivery`
into a proper entity/repository) is needed before `odaEnabled` can be turned back on for
Delhivery.

---

## 2026-09-05 — Added GST (18% forward charge, CGST+SGST/IGST split)

**What**

`RateBreakdown` never had a tax field - neither the original `RateCalculator_old` nor the
Strategy-pattern port included GST. Confirmed with the user: 18% forward charge, split
CGST+SGST for intra-state shipments or IGST for inter-state, computed on top of the
partner's pre-tax `total`.

Kept this out of `DelhiveryRateStrategy` (and every future partner strategy) deliberately -
GST law doesn't vary by delivery partner, so it runs once in `RateCalculator.calculate()` /
`calculateForAllServiceablePartners()`, after the strategy returns its pre-tax breakdown,
via a new stateless `GstCalculator.apply(breakdown, request)`. Every partner gets it
automatically; no strategy needs to know GST exists.

Determining intra- vs inter-state needed the shipment's actual state names, which
`ShipmentRequest` didn't retain (only `Zones`, which spans multiple states, and
`destStateOrCity` for the Delhivery rate-override lookup) - added `sourceStateOrCity` and
wired `RateServiceImpl` to populate it from `RateQuoteRequest.sourceState()`.

**Caveat flagged to the user**

The same-state-name check here is a simplification, not a full implementation of GST's
statutory place-of-supply rules for GTA/courier services (IGST Act s.12(8)). Recommended
verifying against their actual registration/invoicing setup with their CA before relying
on this for real invoices - not something I can determine from the code.

**Files changed**

- New: `rate/GstCalculator.java` (18% split logic; defaults to inter-state/IGST when
  either state is null/unknown, rather than risk a wrong CGST+SGST split)
- Modified: `constants/Constants.java` (added `GST_RATE_PERCENT = 18.0`),
  `rate/dto/RateBreakdown.java` (added `gstRate`, `cgst`, `sgst`, `igst`, `gstAmount`,
  `grandTotal`), `rate/ShipmentRequest.java` (added `sourceStateOrCity`),
  `rate/RateCalculator.java` (calls `GstCalculator.apply` after every strategy call),
  `service/RateServiceImpl.java` (passes `sourceState` through to `ShipmentRequest`)
- New tests: `test/.../rate/GstCalculatorTest.java` (intra-state split, inter-state IGST,
  unknown-state default), `test/.../rate/RateCalculatorTest.java` (confirms GST is the
  calculator's doing and not the strategy's, using a fake `DeliveryRateStrategy`; also
  covers unknown-partner and not-serviceable-partner behavior, which had no test coverage
  before)
- Updated `test/.../rate/delhivery/DelhiveryRateStrategyTest.java`'s hand-verified
  breakdown is unaffected (GST isn't applied at the strategy level, only when going
  through `RateCalculator`)

**Not verified**

Same limitation as every entry above - no JDK 17 / no network for Gradle here, so this
hasn't been compiled or run. Please `./gradlew test` on your machine.

---

## 2026-09-06 — Delhivery now calls their live freight-calculator API, not a local rate card

**What**

The placeholder rate card (`DelhiveryRateSource`/`DelhiveryRateCardConfig`, ported from
`RateCalculator_old`'s sample data) never matched Delhivery's actual contracted rates -
confirmed by comparing our output against a real curl to
`POST https://ucp-egw.delhivery.com/101/api/v1/freight/calculator` the user copied from
their browser session on one.delhivery.com. Several fields differed (base rate/kg,
whether a processing charge applies at all, the ROV floor, whether green tax has a flat
floor or is weight-slab-based) - not rounding bugs, just wrong placeholder numbers.

Rather than trying to guess the real numbers, `DelhiveryRateStrategy` now calls that
live endpoint directly via a new `DelhiveryApiClient` (Spring's `RestClient`) and maps
the response into our `RateBreakdown`. `DelhiveryRateSource` and `DelhiveryRateCardConfig`
are deleted - nothing references them anymore, same reasoning as deleting
`RateCalculator_old` earlier.

**Auth caveat (told to the user directly, repeating here for the record)**

The bearer token in the curl the user shared is a ~10-minute session token scraped from
their own logged-in browser session on Delhivery's dashboard (JWT `iat`/`exp` are 600s
apart; claims show their account `partners@shiporbit.in`) - not a stable, documented
server-to-server API credential. It's stored as `delhivery.api.bearer-token` (env var
`DELHIVERY_API_BEARER_TOKEN`, same pattern as `jwt.secret`), so it can be swapped for a
real API key later with zero code changes, but as-is it needs manual refreshing
constantly and could change/break without notice since it's an internal endpoint, not a
published integration API. Recommended the user ask Delhivery for real API credentials
before relying on this in production.

**Avoiding double GST**

Delhivery's response already includes their own tax-inclusive `total` and
`price_breakup.gst`. `DelhiveryRateStrategy.toRateBreakdown()` deliberately uses only
`price_breakup.pre_tax_freight_charges` as our `RateBreakdown.total` (ignores their `gst`/
`total` fields entirely) so `RateCalculator`'s existing `GstCalculator.apply()` - which
already runs on every partner - computes GST fresh on the correct pre-tax base instead of
taxing an already-tax-inclusive number. Verified this reproduces the user's real numbers
exactly: `1077.6 * 18% = 193.97`, `1077.6 + 193.97 = 1271.57` - identical to what
Delhivery's own API returned.

**Other changes riding along**

- `RateCalculator.calculateForAllServiceablePartners()` now catches per-partner failures
  and logs+skips rather than letting one partner's error (now a real possibility with a
  network call involved, e.g. an expired token) fail the whole comparison. `calculate()`
  (single partner) still throws normally - the caller explicitly asked for that partner.
- New `DelhiveryApiException` (500 -> mapped to 502 Bad Gateway via
  `GlobalExceptionHandler`), with a specific hint when the failure is a 401 (token expired).
- `DelhiveryRateStrategy` now requires `pickupPincode`/`deliveryPincode` on `ShipmentRequest`
  (Delhivery prices by pincode, not by our `Zones` enum) - throws `IllegalArgumentException`
  (400) if either is missing. `sourceState`/`destState` are still required at the API layer
  for GST place-of-supply, so a Delhivery quote now needs both state names and pincodes -
  a bit of overlap, flagged as a known rough edge rather than a bigger request-shape
  refactor.

**Files changed**

- New: `rate/delhivery/DelhiveryApiRequest.java`, `rate/delhivery/DelhiveryApiResponse.java`,
  `rate/delhivery/DelhiveryApiClient.java`, `exception/DelhiveryApiException.java`
- Deleted: `rate/delhivery/DelhiveryRateSource.java`, `rate/delhivery/DelhiveryRateCardConfig.java`,
  `test/.../rate/delhivery/DelhiveryRateSourceTest.java`
- Rewritten: `rate/delhivery/DelhiveryRateStrategy.java` (API call + response mapping instead
  of local computation), `test/.../rate/delhivery/DelhiveryRateStrategyTest.java` (mapping
  test built directly from the user's real sample response, plus a combined test with
  `GstCalculator` that reproduces Delhivery's real `gst`/`total` exactly)
- Modified: `application.yaml` (added `delhivery.api.*`), `exception/GlobalExceptionHandler.java`
  (502 handler), `rate/RateCalculator.java` (per-partner resilience in
  `calculateForAllServiceablePartners`), `rate/RateCardConfig.java` (doc comment no longer
  references the now-deleted `DelhiveryRateCardConfig` as its example)

**Not verified**

Same limitation as every entry above - no JDK 17 / no network for Gradle here, so this
hasn't been compiled or run. The bearer token in the curl the user shared is also already
expired (10-minute lifetime), so it couldn't be tested live even if this environment could
reach the network - please `./gradlew test` and a live smoke test with a fresh token on
your machine.

---

## 2026-09-05 — Rate calculator refactored into a Strategy pattern (multi delivery-partner)

**What**

`RateCalculator_old.java` was a single monolithic class hardcoded to one rate card. The
new `rate` package splits it into a Strategy pattern so each delivery partner (Delhivery
first, more later - e.g. Bluedart, DTDC, Xpressbees) owns its own rate source, config,
and calculation logic, and `RateCalculator` just dispatches to the right one by partner
code. Adding a new partner will only ever mean adding new files under `rate/<partner>/`
- no existing class needs to change (Open/Closed).

Some of these classes already existed as stubs/placeholders before this change
(`DeliveryRateStrategy`, `RateCalculator`, `RateCardConfig`, `RateSource`, `Zones`,
`DelhiveryRateCardConfig`); this fills them in and adds the rest.

**Design**

- `DeliveryRateStrategy` (interface) - one implementation per partner: `getPartnerCode()`,
  `getPartnerName()`, `calculateRate(ShipmentRequest)`, `isServiceable(ShipmentRequest)`.
- `RateCalculator` (context, `@Component`) - Spring injects `List<DeliveryRateStrategy>`
  (every partner bean) and indexes it by partner code. `calculate(partnerCode, request)`
  dispatches to one partner; `calculateForAllServiceablePartners(request)` returns rates
  from every partner that can service the shipment, for a rate-comparison view.
- `ShipmentRequest` (moved from `rate.delhivery` to `rate`, uncommented, rewritten as a
  Lombok `@Data @Builder` class using the existing `dto.Dimension` type) - carrier-agnostic
  input shared by every strategy.
- `RateBreakdown` (moved from `rate.delhivery.dto` to `rate.dto`) - carrier-agnostic output.
- `RateCardConfig` - now an abstract base class holding the freight-config fields common
  across partners (FSC%, ROV%, handling/ODA slabs, conditional delivery charges, etc.).
  `DelhiveryRateCardConfig extends` it and sets Delhivery's specific values (ported from
  `RateCalculator_old`'s `defaultSmeConfig()`), including the volumetric divisor from the
  existing `Constants.DELHIVERY_DEVISOR`.
- `RateSource.getRate(Zones, Zones, String)` - unchanged contract. `DelhiveryRateSource`
  (was an empty marker interface) is now the concrete `@Component` implementing it, with
  the 9x9 zone matrix + state/city overrides ported from `RateCalculator_old`'s
  `InMemoryRateSource`.
- `DelhiveryRateStrategy` - fixed a typo in the existing stub's filename/class name
  (`DelhivaryRateStrategy` -> `DelhiveryRateStrategy`) and implemented `calculateRate()`,
  porting the full calculation (weight, freight, FSC, processing, ROV, handling, ODA,
  conditional add-ons, green tax, subtotal/total) from `RateCalculator_old.calculate()`.
- `RiskType` - pulled out of `RateCalculator` into its own top-level enum in `rate/`, since
  it belongs to the request/strategy contract, not to the context class.
- `Zones` - added a `C` (Central) zone for Madhya Pradesh, which the old `Zone` enum in
  `RateCalculator_old` had (as `CENTRAL`) but the new enum was missing; also fixed a few
  spelling typos in zone/state names (Chandigarh, Himachal Pradesh, Uttarakhand, Ladakh,
  Arunachal Pradesh).

**Files changed**

- New: `rate/RiskType.java`, `rate/ShipmentRequest.java`, `rate/dto/RateBreakdown.java`
- Rewritten: `rate/DeliveryRateStrategy.java`, `rate/RateCalculator.java`,
  `rate/RateCardConfig.java`, `rate/Zones.java`,
  `rate/delhivery/DelhiveryRateSource.java`, `rate/delhivery/DelhiveryRateCardConfig.java`
- Renamed + implemented: `rate/delhivery/DelhivaryRateStrategy.java` ->
  `rate/delhivery/DelhiveryRateStrategy.java`
- Removed (superseded by the files above): `rate/delhivery/ShipmentRequest.java`,
  `rate/delhivery/dto/RateBreakdown.java`
- Unchanged: `rate/RateSource.java` (contract was already correct)

**Not wired up yet**

No controller/service calls `RateCalculator` yet - nothing else in the codebase referenced
any of these classes before this change, so this was a safe, self-contained refactor.
Whoever adds the "get a rate" endpoint should inject `RateCalculator` and build a
`ShipmentRequest` from the API request.

**Not verified**

Same limitation as prior entries: no JDK 17 and no network to fetch the Gradle
distribution in this environment, so `./gradlew compileJava` could not be run. Reviewed
by hand for correctness against the ported logic in `RateCalculator_old`; please build on
your machine before relying on this.

**Follow-up (same day)**

Deleted `rate/RateCalculator_old.java` - it was fully commented out (kept only as a
reference while porting its logic above) and nothing in the codebase referenced it.

## 2026-08-31 — `app.security.enabled=false` still required authentication

**Problem**

Setting `app.security.enabled: false` in `application.yaml` was meant to let all endpoints
work without a JWT. Instead, requests without a token still failed. Two independent causes:

1. **`JwtAuthenticationFilter` ran even when disabled.** It's annotated `@Component`, so
   Spring Boot auto-registers it as a global servlet filter regardless of whether
   `SecurityConfig` wires it into the Spring Security chain via `addFilterBefore(...)`.
   The disabled branch never called `addFilterBefore`, but the filter still ran on every
   request because Spring Boot registered it independently.

2. **Controllers unconditionally cast the principal.** `PickUpAddressController`,
   `AuthController`, and `AuthServiceImpl` all do
   `(ShipOrbitUserPrincipal) authentication.getPrincipal()`. Even with
   `anyRequest().permitAll()`, Spring Security's default `AnonymousAuthenticationFilter`
   still runs (it's part of `HttpSecurity`'s default configurer set, never disabled here),
   so `Authentication` is never `null` — its principal is the plain string
   `"anonymousUser"` when no token is sent. The cast to `ShipOrbitUserPrincipal` then threw
   a `ClassCastException`, caught by `GlobalExceptionHandler`'s catch-all and turned into a
   500 — functionally indistinguishable from "still needs auth."

**Why this fix**

- For (1): register a `FilterRegistrationBean<JwtAuthenticationFilter>` with
  `setEnabled(false)`. This is the standard Spring Boot pattern for a filter that should
  only run when a security config explicitly wires it in, not globally. Zero risk to the
  enabled path — `addFilterBefore` still adds it to Spring Security's own chain exactly as
  before.
- For (2): rather than touching every controller (6 call sites) to handle a `null`/anonymous
  principal, add a small `DevAuthenticationFilter` that is only wired in the disabled
  branch. It resolves one configured "dev user" (`app.security.dev-user-email`) via the
  existing `DatabaseUserDetailsService` and sets it as the authenticated principal for every
  request. Existing controller code needed no changes. Confirmed with the user
  (Saurabh) that this "configurable dev user" approach was preferred over (a) picking the
  first user in the DB implicitly, or (b) disabling anonymous auth and updating every
  controller call site.
- The dev user is resolved once, at `SecurityFilterChain` bean creation (app startup), and
  the app now fails fast with a clear `IllegalStateException` if `dev-user-email` is blank
  while security is disabled, or a clear `UsernameNotFoundException` if that email doesn't
  exist — instead of failing confusingly on the first API call.

**Files changed**

- `backend/src/main/java/com/shiporbit/backend/security/DevAuthenticationFilter.java` — **new file.**
  Stand-in for the JWT filter when security is disabled; attaches the configured dev user as
  the authenticated principal on every request.
- `backend/src/main/java/com/shiporbit/backend/security/SecurityConfig.java` — modified:
  - Injected `UserDetailsService`.
  - Added `app.security.dev-user-email` config binding.
  - Disabled branch now validates `dev-user-email` is set, resolves that user via
    `UserDetailsService`, and wires `DevAuthenticationFilter` with `addFilterBefore(...)`
    instead of doing nothing.
  - Added a `FilterRegistrationBean<JwtAuthenticationFilter>` bean with `setEnabled(false)`
    so the JWT filter can no longer run outside of the explicit `addFilterBefore` call in
    the enabled branch.
- `backend/src/main/resources/application.yaml` — modified: added
  `app.security.dev-user-email` (blank by default) with a comment explaining it's required
  when `enabled: false`.

**Action needed from you**

`app.security.dev-user-email` is currently blank. Since `app.security.enabled` is already
`false` in this file, the app will now refuse to start until you set
`app.security.dev-user-email` to the email of a real user already in the database (e.g. one
you created earlier via the signup endpoint while security was on).

**Not verified**

Could not run `./gradlew compileJava` in this environment — the Gradle wrapper needs to
download its distribution and this shell has no network access, and the system JDK here is
11 while the project uses `jakarta.*` (Spring Boot 3, needs 17+). Changes were reviewed by
hand for correctness; please run a build on your machine before relying on this.

---

## 2026-08-31 — Seeded a dev user for `app.security.dev-user-email`

**Problem**

The previous change requires `app.security.dev-user-email` to point at a real row in
`SO_USERS`. With H2 in-memory, the schema is rebuilt from scratch on every startup, so
there was no existing user to point it at.

**Why this fix**

Added one row to `data.sql` (which already seeds roles and states the same way), rather
than asking for a manual signup-then-copy-the-email step every time the DB resets. Gave it
a real bcrypt password hash too (not just filler), so the same account still works to log
in through `/api/v1/auth/login` if `app.security.enabled` is ever flipped back to `true`.

**Files changed**

- `backend/src/main/resources/data.sql` — modified: appended one `INSERT INTO SO_USERS`
  row.
  - id: `11111111-1111-1111-1111-111111111111`
  - email: `dev@shiporbit.local`
  - password: `DevPassword123!` (stored as a bcrypt hash, generated with the same
    `BCryptPasswordEncoder` the app uses)
  - role: `ADMIN` (role_id 1)
- `backend/src/main/resources/application.yaml` — modified: `app.security.dev-user-email`
  now set to `dev@shiporbit.local` (was blank) so the app runs as-is with the seed data.

**Not verified**

Same limitation as the previous entry — no working Gradle/JDK17 environment here to run
the app and confirm data.sql inserts cleanly and the server actually starts. Please start
the app locally and confirm.


---

## 2026-09-06 — Traced the Delhivery live-API integration for correctness; fixed a real bug in `rov_insurance`

**Problem**

You asked for actual verification that the new Delhivery live-API integration
(`DelhiveryRateStrategy`, `DelhiveryApiRequest/Response`, `DelhiveryApiClient`) is correct,
not just my assurance that it is. I can't compile or run this code here, so I traced the
outgoing request our code builds field-by-field against your real, working curl to
`https://ucp-egw.delhivery.com/101/api/v1/freight/calculator`
(source_pin=110084, target_pin=500001, weight_g=20000, invoice_amount=1,
payment_mode=prepaid, dimensions=[{60,60,60,"cm",1}], freight_mode=null, fm_pickup=true,
self_collect=false, rov_insurance=false).

Result of the trace:
- paymentMode, weightG, invoiceAmount, sourcePin/targetPin, freightMode, fmPickup,
  selfCollect, dimensions all matched your real request exactly.
- `rovInsurance` did NOT match. The code was deriving it as
  `req.getRiskType() == RiskType.CARRIER`, which sends `true` for our default risk type
  (`ShipmentRequest.riskType` defaults to `CARRIER`). Your real curl sent
  `rov_insurance: false` and still got charged `insurance_rov: 150.0` back — which
  contradicts the assumption that this flag gates whether ROV insurance is charged, and
  means the RiskType-derived mapping was unverified and likely wrong.

**Why this fix**

With only one real data point (a request that sent `false` and still got the insurance
charge), there's no evidence to support deriving this flag from `RiskType` at all - that
was a guess. The safest fix, until we see a real example with `rov_insurance: true` to
learn what it actually controls, is to hardcode it to match the one example we know works,
with a comment explaining why, rather than keep an unverified derivation.

**Files changed**

- `backend/src/main/java/com/shiporbit/backend/rate/delhivery/DelhiveryRateStrategy.java` —
  modified: `rovInsurance` is now hardcoded to `false` (was
  `req.getRiskType() == RiskType.CARRIER`), with a comment explaining the real-world
  evidence and what would justify changing it. Removed the now-unused `RiskType` import.
- `backend/src/test/java/com/shiporbit/backend/rate/delhivery/DelhiveryRateStrategyTest.java`
  — modified: added `buildsApiRequestMatchingTheRealWorkingCurlExample`, which uses
  `Mockito.mock(DelhiveryApiClient.class)` + `ArgumentCaptor<DelhiveryApiRequest>` to capture
  the exact request `calculateRate()` builds and assert every field against your real curl.
  This is the test that would have caught the `rovInsurance` bug, and should catch any
  future drift in this mapping.

**Still open / not verified**

- `resolvePaymentMode()`'s `"cod"` and `"topay"` string values are still an unverified
  guess — only the `"prepaid"` branch has a real confirmed example. If you get a real
  COD or to-pay quote, share it so we can confirm (or fix) those two values the same way.
- Could not run `./gradlew test` here (no JDK 17 / no network access to Gradle's
  distribution server in this environment) - the trace above and the new test are correct
  by hand-verification against your real data, but please run
  `./gradlew test --tests "*DelhiveryRateStrategyTest*"` locally to get a real, compiled
  confirmation before relying on this.


---

## 2026-09-06 — Enabled application logging (console pattern + file, and request/response tracing on the rate/Delhivery path)

**Problem**

The app had no dedicated logging config beyond `org.springframework.security: DEBUG`, and
only a handful of classes (AuthController, PickUpAddressController, GlobalExceptionHandler,
RequestInterceptor, RateCalculator) had a logger at all. Nothing logged the actual
request/response going to/from Delhivery, which made problems like the `rov_insurance`
mapping bug in the previous entry harder to see from running logs alone.

**What**

- `application.yaml` — added `logging.level.com.shiporbit.backend: DEBUG`, a timestamped
  console pattern (`logging.pattern.console`), and file output
  (`logging.file.name: logs/shiporbit-backend.log`, already gitignored).
- Added SLF4J loggers (same manual `LoggerFactory.getLogger(...)` pattern already used in
  `RateCalculator`/`GlobalExceptionHandler`) to the classes on the rate-quote request path
  that had none:
  - `RateController` — logs every incoming quote/compare-all request (partner, pickup/
    delivery pincode, weight) at INFO, and the result total at DEBUG.
  - `RateServiceImpl` — logs the state->zone resolution at DEBUG.
  - `DelhiveryApiClient` — logs the outgoing request shape (pins, weight, payment mode,
    rovInsurance) and the response shape (chargedWt, preTaxFreightCharges) at DEBUG, and
    failures at WARN. Deliberately never logs the bearer token itself.
  - `DelhiveryRateStrategy` — logs the final pre-tax quote (pins, chargeable weight, total)
    at INFO.

**Why this fix**

You asked to enable logging so you can watch the app work while building the pincode-
serviceability feature in parallel. Focused the new logging specifically on the rate/
Delhivery path since that's been the source of every real bug found so far (ODA,
rov_insurance) - having the actual outgoing request and incoming response in the logs
means the next discrepancy can be diagnosed from `logs/shiporbit-backend.log` directly,
without needing another side-by-side curl comparison.

**Files changed**

- `backend/src/main/resources/application.yaml`
- `backend/src/main/java/com/shiporbit/backend/controller/RateController.java`
- `backend/src/main/java/com/shiporbit/backend/service/RateServiceImpl.java`
- `backend/src/main/java/com/shiporbit/backend/rate/delhivery/DelhiveryApiClient.java`
- `backend/src/main/java/com/shiporbit/backend/rate/delhivery/DelhiveryRateStrategy.java`

**Not verified**

No JDK17/no network for Gradle here, as always - please start the app and confirm you see
timestamped logs in the console and in `logs/shiporbit-backend.log`, and that a rate-quote
request produces the expected INFO/DEBUG lines.


---

## 2026-09-13 — Wallet + PayU top-up module (new)

**Problem**

ShipOrbit needs an internal customer wallet (top-up via a payment gateway, debit for
shipment booking) before shipment booking itself can charge customers. No wallet, payment,
or Order/Shipment entity existed yet - this is new ground, not a fix to existing code.

**What**

- `db/migration/V3__wallet.sql` — `SO_WALLET` (one row per user, `BALANCE` +
  optimistic-lock `VERSION`, FK to `SO_USERS`, `CHECK (BALANCE >= 0)`) and
  `SO_WALLET_TRANSACTION` (append-only ledger: TOPUP/DEBIT/CREDIT rows with
  PENDING/SUCCESS/FAILED status, unique `REFERENCE_ID` for idempotency, unique nullable
  `GATEWAY_TXN_ID`).
- `entity/WalletEntity`, `entity/WalletTransactionEntity` (+ `WalletTransactionType`,
  `WalletTransactionStatus` enums) — same style as `PickupEntity`/`Users`
  (`@ManyToOne` to `Users`, `@PrePersist`/`@PreUpdate` timestamps).
- `repository/WalletRepository` — `findByUserIdForUpdate()` takes a
  `@Lock(PESSIMISTIC_WRITE)` row lock; this, not the `@Version` column, is what actually
  prevents two concurrent debits (or a debit racing a top-up credit) from overdrawing a
  wallet. `WalletTransactionRepository` adds the reference/gateway-txn-id lookups and a
  paginated history query.
- `payment.routing.PayUConfigProperties`, `payment.service.PayUClientConfig` (unused
  `RestClient` bean today - reserved for the `verify_payment` reconciliation call, see
  below), `payment.service.PaymentGatewayClient` (strategy interface so a second gateway
  can be added later) + `PayUPaymentGatewayClient`, `payment.util.PayUHashUtil`
  (request-hash + reverse/response-hash per
  https://docs.payu.in/docs/generate-hash-payu-hosted, checked September 2026).
- `service/WalletService` + `WalletServiceImpl` — `getBalance` (lazily creates a
  zero-balance wallet), `getHistory` (paginated), `initiateTopUp` (creates a PENDING ledger
  row, returns the PayU hosted-checkout form fields), `handlePayUCallback` (verifies the
  hash, idempotent on `REFERENCE_ID` so a replayed postback is a no-op, credits under the
  pessimistic lock), `debit`/`credit` (idempotent on caller-supplied `referenceId`, for the
  future shipment-booking flow to call).
- `controller/WalletController` (`/api/v1/wallet`: `GET /balance`, `GET /transactions`,
  `POST /topup`) and `controller/PaymentCallbackController`
  (`POST /api/v1/payments/payu/callback`, public - PayU posts here directly, no JWT).
- `SecurityConfig` — added `/api/v1/payments/payu/**` to the public matcher list.
- `exception/` — `InsufficientBalanceException` (422), `WalletNotFoundException` (404),
  `PaymentGatewayException` (502), `DuplicateTransactionException` (409), plus an
  `ObjectOptimisticLockingFailureException` handler (409) - all wired into
  `GlobalExceptionHandler`.
- `application.yaml` — new `payment.payu.*` block (`PAYU_MERCHANT_KEY`/`PAYU_MERCHANT_SALT`
  env vars, sandbox `base-url` defaulted to `https://test.payu.in`). Added the same two env
  var names as empty placeholders to `.env` (gitignored) - you still need to fill in real
  sandbox values from your PayU account before this can be exercised end-to-end.
- Tests: `PayUHashUtilTest` (independently re-derives the expected SHA-512 digest from a
  literal pipe string and compares against the utility, plus tamper-detection checks) and
  `WalletServiceImplTest` (Mockito - insufficient balance, successful debit + ledger entry,
  idempotent replay of a debit and of a PayU callback, successful credit-on-callback).

**Why this fix**

Wallet is the prerequisite for shipment booking to actually charge customers. PayU was the
chosen gateway; the wallet is scoped as an internal credit ledger only (no
withdraw/transfer-out), which avoids RBI PPI (prepaid instrument) licensing - money only
ever moves in via PayU top-up or out via a shipment debit, never back out to the customer's
bank account. No Order/Shipment entity exists yet, so `debit`/`credit` are generic
`(userId, amount, referenceId, remarks)` calls a future booking flow will call, not tied to
an order id today.

**Pending / not yet done**

1. PayU's `verify_payment` server-to-server reconciliation API is NOT implemented -
   `PayUPaymentGatewayClient.verifyCallback()`'s hash check is the only trust boundary
   today. `PayUClientConfig`'s `RestClient` bean is reserved for this, same pattern as
   `DelhiveryClientConfig` predating the calls that use it.
2. `PaymentCallbackController` returns a bare 200 after processing - it should redirect the
   browser to a real frontend page, but that URL hasn't been confirmed with the frontend
   yet.
3. No admin/support endpoint to manually adjust a wallet balance - self-service top-up and
   internal debit/credit only.
4. `TopUpRequest` asks for `phoneNumber` directly because `Users` has no phone field -
   worth revisiting if/when the user profile grows one.
5. Non-zero, non-happy-path test coverage for `handlePayUCallback` (e.g. a genuinely failed
   PayU payment, an amount mismatch) isn't written yet - only the success and idempotent-
   replay paths are covered.

**Files changed**

- `backend/src/main/resources/db/migration/V3__wallet.sql` (new)
- `backend/src/main/java/com/shiporbit/backend/entity/WalletEntity.java` (new)
- `backend/src/main/java/com/shiporbit/backend/entity/WalletTransactionEntity.java` (new)
- `backend/src/main/java/com/shiporbit/backend/entity/WalletTransactionType.java` (new)
- `backend/src/main/java/com/shiporbit/backend/entity/WalletTransactionStatus.java` (new)
- `backend/src/main/java/com/shiporbit/backend/repository/WalletRepository.java` (new)
- `backend/src/main/java/com/shiporbit/backend/repository/WalletTransactionRepository.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/routing/PayUConfigProperties.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/service/PayUClientConfig.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/service/PaymentGatewayClient.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/service/PayUPaymentGatewayClient.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/util/PayUHashUtil.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/dto/PaymentInitiationRequest.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/dto/PaymentInitiationResult.java` (new)
- `backend/src/main/java/com/shiporbit/backend/payment/dto/PaymentVerificationResult.java` (new)
- `backend/src/main/java/com/shiporbit/backend/dto/TopUpRequest.java` (new)
- `backend/src/main/java/com/shiporbit/backend/dto/TopUpInitiationResponse.java` (new)
- `backend/src/main/java/com/shiporbit/backend/dto/WalletBalanceResponse.java` (new)
- `backend/src/main/java/com/shiporbit/backend/dto/WalletTransactionResponse.java` (new)
- `backend/src/main/java/com/shiporbit/backend/service/WalletService.java` (new)
- `backend/src/main/java/com/shiporbit/backend/service/WalletServiceImpl.java` (new)
- `backend/src/main/java/com/shiporbit/backend/controller/WalletController.java` (new)
- `backend/src/main/java/com/shiporbit/backend/controller/PaymentCallbackController.java` (new)
- `backend/src/main/java/com/shiporbit/backend/exception/InsufficientBalanceException.java` (new)
- `backend/src/main/java/com/shiporbit/backend/exception/WalletNotFoundException.java` (new)
- `backend/src/main/java/com/shiporbit/backend/exception/PaymentGatewayException.java` (new)
- `backend/src/main/java/com/shiporbit/backend/exception/DuplicateTransactionException.java` (new)
- `backend/src/main/java/com/shiporbit/backend/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/shiporbit/backend/security/SecurityConfig.java`
- `backend/src/main/resources/application.yaml`
- `backend/.env` (gitignored - added empty `PAYU_MERCHANT_KEY`/`PAYU_MERCHANT_SALT`)
- `backend/src/test/java/com/shiporbit/backend/payment/util/PayUHashUtilTest.java` (new)
- `backend/src/test/java/com/shiporbit/backend/service/WalletServiceImplTest.java` (new)

**Not verified**

No network access to Gradle's distribution server in this environment, same as every
previous entry in this file - please run `./gradlew clean test` locally (or build in
IntelliJ) before relying on this. I checked brace/paren balance and cross-checked every
import by hand, but that is not a substitute for an actual compile. Also fill in
`PAYU_MERCHANT_KEY`/`PAYU_MERCHANT_SALT` in `.env` from your PayU sandbox account - nothing
in this module can be exercised end-to-end without them.
