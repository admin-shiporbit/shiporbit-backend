# ShipOrbit Rate Calculator: Context for New Chat

## Me / how to answer
- Saurabh, Lead Software Engineer (Java, Spring Boot, AWS, Kafka). Building **ShipOrbit**, a delivery aggregator (India).
- Answers: terse, precise, no filler. Don't present guesses as facts; say when something is unverified.

## Project
- Backend repo: `/Users/saurabh/Developer/personal/shiporbit/dev/shiporbit-backend/backend`, branch **`rate-calculator`**
- Stack: Spring Boot **4.1.0**, Java 21, Gradle (war), JPA/Hibernate 7, **Flyway 12.4**, PostgreSQL 16 (docker-compose, container `shiporbit-postgres`, db/user `shiporbit`), Lombok, RestClient, Spring Security + JJWT, Actuator, dotenv.
- Conventions: `CODING_PRACTICES.md` in repo (layers controller → service → rate strategy → repo; BigDecimal for money, HALF_UP per line; records for DTOs; `SO_` table prefix; constructor injection; migrations append-only).
- Guide written: `docs/RATE_CALCULATOR_GUIDE.md` (patterns, packages, build order, golden tests).
- Sample request/response JSON: `docs/samples/quote_request_{intercity,samecity}.json`, `quote_response_*.json`.
- Stray empty folder `.claude-tmp` in repo root: delete it.

## Existing rate code (live partner APIs)
- `rate/service/DeliveryPartnerClient` (partnerCode, isServiceable, getRate) implemented by Delhivery (LTL), ShreeMurti, UPS, DHL, Fake. Aggregated in `RateAggregatorServiceImpl` (list-injected registry), wrapped by `MarkupPartnerClient` (decorator).
- Known issues: markup hard-coded 1.10 and applied on GST too; `compareRates` swallows failures; `RequestParamRecord` uses Double + single Dimension; Delhivery `isServiceable` is a TODO returning true; endpoint `/api/rate/aggregator` (not /api/v1); `PincodeEntity.pinCode` maps to `pin_code` but column is `pincode`; `change.md` mentions classes that no longer exist.

## Flyway / branches
- Shared local DB across branches. Versions in use: `notification` = V3–V5 (applied locally), `payment-gateway` = `V3__wallet.sql` (**clashes; rename to V9**), `rate-calculator` = **V6–V8** (mine).
- On rate-calculator branch, run locally with program arg `--spring.flyway.ignore-migration-patterns=*:missing` (IntelliJ run config) until `notification` is merged; do `./gradlew clean` / Rebuild first. Never `flyway repair`, never edit applied migrations.
- Rule: next free version across ALL branches (or timestamp versions like `V2026_10_11_1__x.sql`).

## Goal
Rate-card based calculator for **14 partners**: one JSON request → every partner's services with full breakdown (or status + reason). Partners differ only in DATA; calculator code is generic.

| Pricing model | Partners (ID NAME in SO_DELIVERY_PARTNER_LIST) |
|---|---|
| ZONE_SLAB (courier) | 1 DELHIVERY, 2 BLUEDART, 3 DTDC, 4 AMAZON_SHIPPING, 8 SHADOWFAX, 10 EKART, 13 SHREE_MARUTI, 14 XPRESSBEES |
| DISTANCE_BAND | 6 INDIA_POST |
| B2B_FREIGHT | 7 RIVIGO (now MLL Express), 9 TCI, 12 SAFEXPRESS |
| HYPERLOCAL | 5 BORZO, 11 PIKNDEL, 8 SHADOWFAX (second service) |

## Tables (V6__rate_card_schema.sql) + existing `SO_DELIVERY_PARTNER_LIST` (V7 added ids 4–14)
1. **SO_RATE_SERVICE**: partner × service (`SERVICE_CODE`, `PRICING_MODEL`, `MODE`, `ENABLED`).
2. **SO_RATE_CARD**: version per service: `STATUS` DRAFT/ACTIVE/RETIRED (one ACTIVE per service, partial unique index), `DATA_STATUS` SAMPLE/CONTRACT, effective dates, `VOLUMETRIC_DIVISOR`, `CFT_KG`, min chargeable kg, max weight, max km, `MPS_RULE`, `RTO_RULE`/`RTO_PERCENT`, `MIN_BILL`, `RATES_INCLUDE_GST`.
3. **SO_RATE_SLAB**: base freight. `DIRECTION` FORWARD/REVERSE, `LOCATION_KEY` (zone / distance band / B2B zone pair `N1-S1` / vehicle `BIKE`), `UNIT` KG/KM, row applies when `FROM_UNIT < x <= TO_UNIT`; freight = `FIRST_RATE + CEIL((x−FIRST_UNIT)/ADD_UNIT) × ADD_RATE` (no add part if x ≤ FIRST_UNIT or ADD_UNIT = 0). Lookup-table partners (e.g. Amazon) = one row per weight step with ADD = 0.
4. **SO_RATE_CHARGE_RULE**: one row per extra charge, applied by `SEQ`. `CALC_TYPE` FLAT (FIXED_AMOUNT) / PERCENT (FIXED + RATE% × base) / PER_UNIT (RATE × chargeable), clamp MIN/MAX. `BASE` BASE_FREIGHT / CHARGE_CODES (+`BASE_CODES`, e.g. `BASE,ODA`) / COD_AMOUNT / DECLARED_VALUE / NONE. `TIER_FROM/TO` on base value. `APPLIES_WHEN` ALWAYS, COD, PREPAID, ODA_DESTINATION, ODA_PICKUP, SPECIAL_CITY, RISK_OWNER, RISK_CARRIER, ADDON_OTP, ADDON_QC, ADDON_FRAGILE. `DIRECTIONS` csv. Not-levied charges stored as 0 (explicit). Freight line code = `BASE`.
5. **SO_ZONE_MAP**: PK (PARTNER_ID, PRICING_MODEL, ORIGIN_PINCODE, DESTINATION_PINCODE) → LOCATION_KEY. Not used for HYPERLOCAL.
6. **SO_PARTNER_PINCODE**: PK (PARTNER_ID, PINCODE): CITY_CODE (same-city check for hyperlocal), STATE_ID, PICKUP, DELIVERY, COD, PREPAID, REVERSE_PICKUP, ODA, SPECIAL_CITY.

Linking: partner → service → card → slabs + rules; zone map + pincodes belong to partner. Zone map LOCATION_KEY must equal slab LOCATION_KEY names.

## Seed data (V8__seed_sample_rate_cards.sql): all SAMPLE values
- 21 services / 21 ACTIVE cards / 317 slabs / 174 rules / 1,200 zone rows / 126 pincode rows.
- Test pincodes: 201301 Noida, 110001 Delhi, 400001 Mumbai, 560001 Bengaluru, 600001 Chennai, 700001 Kolkata, 781001 Guwahati, 190001 Srinagar, 194101 Leh, 744101 Port Blair.
- Verified on PG16 (psql, per-file txn): all checks passed; recomputed 42 quotes from tables = JSON samples exactly.
- Golden (201301→560001, COD ₹1,499, declared ₹1,499, 1×0.8 kg 30×20×10, OWNER, margin 10%, GST 18%): Delhivery Surface 0.5kg 219.36, Express 275.18, Surface 5kg 324.50; Bluedart Apex 634.72, Surfaceline 472.37; Amazon 190.81; Shadowfax 192.10; Xpressbees 184.32 / 199.89 / 171.34; Ekart 203.79 / 2kg Flat 94.05; DTDC 300.88; Shree Maruti 192.10; India Post 229.75; Rivigo 797.62; Safexpress 877.45; TCI 785.14; hyperlocal = NOT_SERVICEABLE. Same-city 12 km bike: Borzo 201.19, PikNDel 185.61, Shadowfax HL 160.95.

## Calculator design (not coded yet)
- Patterns: **Template Method** `AbstractRateCardCalculator.quote()` (final pipeline) with subclasses per pricing model (ZoneSlab, DistanceBand, B2bFreight, Hyperlocal); **Strategy** per charge calc type; ordered `ChargeEngine`; **Registry** `Map<PricingModel, calculator>`; **Specification** eligibility rules returning status codes; **Decorator** for margin (margin on pre-tax, then GST); **Adapter** for existing live API clients; **sealed QuoteResult** (Priced / Unavailable).
- Runtime: service (enabled) → ACTIVE card for pickup date → pincode checks → zone map → chargeable weight (max(dead, vol/divisor or CFT, min)) → slab → rules by SEQ → min bill → margin → GST.
- Statuses: OK, VALIDATION_ERROR, DISABLED, CONFIG_INCOMPLETE, ZONE_UNKNOWN, NOT_SERVICEABLE, COD_NOT_AVAILABLE, WEIGHT_LIMIT_EXCEEDED, RATE_UNAVAILABLE, DISTANCE_REQUIRED, PARTNER_API_ERROR.
- API: `POST /api/v1/rates/quotes`; response `results[]` per partner → `services[]` with `breakdown[]` (code, amount), zone, chargeable weight, slab info, `cheapestForPartner`, top-level `cheapest`.
- Parallel provider calls with Java 21 virtual threads; Caffeine cache on rate cards (evict on activation).
- Gradle additions planned: `spring-boot-starter-cache` + caffeine; `spring-boot-testcontainers` + Testcontainers PostgreSQL (2.x module names, verify); later Apache POI (import), springdoc 3.x (verify version). No rules engine.

## Partner data import (key understanding)
- Different partner file formats → **different import converter per format**; tables and calculator unchanged.
  - FastShip-style: explicit pair zone list → copied to SO_ZONE_MAP.
  - SlowDelivery-style: pincode → partner zone code + zone matrix → we compute pair rows (origins = our pickup pincodes × destinations).
- Real Amazon file analysed (`SHIP-ORBIT_8998775448_2026-10-08.xlsx`): sheets Standard, Standard (Reverse leg), Express, Express (Reverse leg); weights 0.5–99.5 kg (199 steps); regions Local/Regional/Metro/National/Remote; footer DIM Div 5000, COD fee 30; reverse = forward; prices irregular (lookup table) → 199 rows/region; above 5 kg price changes per full kg; Standard jumps to flat ₹1,999 from 61 kg; Express flat from 62 kg (both to confirm). → 2 services, 2 cards, 3,980 slab rows. Missing from Amazon: zone mapping, pincodes, RTO rule, COD % or flat, fuel/ODA/GST, validity, real max weight.

## Open: possible schema gaps (decide before coding; would be V9+ migration + small code)
1. Slab `PAYMENT_MODE` (different COD vs prepaid base rates)
2. Slab `SHIPMENT_TYPE` (document vs parcel)
3. Rule `BASE = 'CHARGEABLE_WEIGHT'` (weight-tiered charges)
4. Rule `VALID_FROM/VALID_TO` (peak/festive surcharge dates)
5. Card `ROUNDING` (CEIL_STEP vs EXACT for per-kg)
6. Rule category condition (`APPLIES_WHEN = 'CATEGORY'` + value)
Zone-by-rules partners are handled at import (generate pair rows).

## Other artifacts made earlier
- Spec doc (Claude Docs): "ShipOrbit Rate Calculator – Input & Charge Spec".
- `ShipOrbit_Rate_Calculator.xlsx` (sample calculator) + HTML version "ShipOrbit Rate Calculator" (artifact).
- `ShipOrbit_Partner_Inputs_Checklist.xlsx` (what to collect from each of 14 partners, incl. B2B + hyperlocal items).

## Next steps
1. Decide on gaps 1–6 above (add as one migration if yes).
2. JPA entities + repositories for the 6 tables (`@DataJpaTest` on Testcontainers PG).
3. Domain records/enums + QuoteRequest validation.
4. ChargeEngine + handlers → ZoneSlabCalculator + eligibility → RateCardProvider → golden tests.
5. `/api/v1/rates/quotes` + margin/GST + adapters for live API clients.
6. Other calculators (DistanceBand, B2B, Hyperlocal), cache, importer (Amazon first).
