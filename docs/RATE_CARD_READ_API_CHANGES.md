# Rate Card: Read API and Entity Fixes (11 Oct 2026)

Branch: `rate-calculator`. Nothing committed.

**Status:** not compiled. Gradle could not download its distribution and dependencies, either on your Mac's sandbox or in my cloud workspace (proxy blocked). All usages were checked by hand. Run `./gradlew clean compileJava` and then start the app to confirm.

---

## Summary

| # | File | Action |
|---|---|---|
| 1 | `src/main/resources/db/migration/V10__rate_card_extensions.sql` | Added (earlier this session) |
| 2 | `rate/entity/RateServiceEntity.java` | Edited (bug fixes) |
| 3 | `rate/repository/RateServiceRepository.java` | Edited (bug fix) |
| 4 | `rate/repository/RateCardRepository.java` | Edited (bug fixes) |
| 5 | `rate/dto/response/RateCardViews.java` | Added |
| 6 | `rate/service/RateCardQueryService.java` | Added |
| 7 | `rate/api/RateCardController.java` | Added |
| 8 | `docs/RATE_CARD_READ_API_CHANGES.md` | Added (this file) |
| 9 | `repository/DeliveryPartnerRepository.java` | Added (partner names on `/lane`) |
| 10 | `RateCardViews.java`, `RateCardQueryService.java` | Edited again (partner and state names on `/lane`) |

All Java paths are under `src/main/java/com/shiporbit/backend/`.

---

## 1. `V10__rate_card_extensions.sql` (added)

**Why:** adds the 6 schema extensions ("gaps") and sample rows that use them. The data is SAMPLE, so we decided to add them now rather than later.

**Schema:**
- `SO_RATE_SLAB`: `PAYMENT_MODE` (COD/PREPAID) and `SHIPMENT_TYPE` (DOCUMENT/PARCEL); NULL means any. The unique constraint `SO_UQ_RATE_SLAB` is replaced by the unique index `SO_UX_RATE_SLAB`, which includes both columns.
- `SO_RATE_CARD`: `ROUNDING` (`CEIL_STEP` default / `EXACT`).
- `SO_RATE_CHARGE_RULE`: `VALID_FROM`, `VALID_TO`, `APPLIES_VALUE`. `BASE` also accepts `CHARGEABLE_WEIGHT`; `APPLIES_WHEN` also accepts `CATEGORY` (which requires `APPLIES_VALUE`).

**Sample data:** B2B cards use `EXACT` rounding; Ekart 2kg prepaid slabs; DTDC document slabs; Delhivery peak surcharge (25 Oct to 15 Nov 2026); Bluedart electronics surcharge; Rivigo heavy-handling tiers by weight.

**Why V10 and not V9:** V9 is reserved for payment-gateway's `V3__wallet.sql` rename.

**Verified:** V1 to V8 plus V10 applied cleanly on a fresh PostgreSQL 16. The golden quotes should be unchanged, because none of the new rows match the golden request (COD, parcel, GENERAL category, 12 Oct, 20 kg for B2B). This was reasoned from the data, not run through a calculator.

---

## 2. `RateServiceEntity.java` (edited)

**Why:** 3 bugs, 1 of which would stop the app starting.

| Before | After | Problem |
|---|---|---|
| `Integer deliveryPartner` | `Integer partnerId` | The repository method `findByPartnerIdAndServiceCode` needs a property named `partnerId`. Spring Data fails at startup with "No property 'partnerId' found". |
| `BigInteger id` + `GenerationType.AUTO` | `Long id` + `GenerationType.IDENTITY` | The column is `BIGSERIAL`, which maps to `Long`, and the repository is typed `<RateServiceEntity, Long>`. On PostgreSQL, `AUTO` looks for a sequence that doesn't exist, so inserts fail. |
| `SERVICE_CODE` with no length | `length = 50` | Matches `VARCHAR(50)` in V6. |
| unused `DeliveryPartnerEntity` import | removed | Option A: store the plain ID, no object relationship. |

---

## 3. `RateServiceRepository.java` (edited)

**Why:** `findByPartnerIdAndServiceCode(String partnerId, ...)` changed to `Integer partnerId`. The column is `INT`; passing a `String` gives a type mismatch at query time.

---

## 4. `RateCardRepository.java` (edited)

**Why:** 2 bugs and 1 missing method.

| Before | After | Problem |
|---|---|---|
| `@Param("statusOn")` but the query uses `:status` | `@Param("status")` | The names don't match, so startup fails. |
| `and (c.effectiveFrom is not null or c.effectiveTo > :date)` | `and (c.effectiveTo is null or c.effectiveTo >= :date)` | The old condition was always true (`effectiveFrom` is never null), so expired cards were still returned. It also used `>`, which excluded the last valid day. |
| no `findActiveFor` | `default findActiveFor(serviceId, date)` | Convenience method that passes `CardStatus.ACTIVE`, used by the service. |
| unused imports (`javax.smartcardio.Card`, `CrudRepository`, `RtoRule`) | removed | `javax.smartcardio` was an IDE auto-import mistake. |

---

## 5. `RateCardViews.java` (added)

**Why:** `CODING_PRACTICES.md` says never expose an entity from a controller. These records are the response shapes.

- One record per entity: `ServiceView`, `CardView` (with `slabs` and `rules`), `SlabView`, `RuleView`, `PincodeView`, `ZoneView`, plus `LaneView` (the pincodes and zones for one lane).
- Each has a static `from(entity)` method, so mapping stays in one place.
- They're grouped in one file because they're all read-only views for the same API. They can be split later if it grows.

---

## 6. `RateCardQueryService.java` (added)

**Why:** controller → service → repository layering. The controller only handles HTTP; the service loads and maps.

- `@Transactional(readOnly = true)`: one read-only transaction per call, so Hibernate skips dirty checking.
- `enabledServices()`: `findByEnabledTrue()`.
- `activeCard(serviceId, date)`: the active card for that date, plus its slabs and rules (in `SEQ` order). Returns empty if no card is active.
- `lane(origin, destination)`: **2 queries in total**, covering every partner: pincode rows for both pincodes, and zone rows for the lane. This is the same access pattern the calculator will use.
- No pricing logic. That goes in step 4 (ChargeEngine and calculators).

---

## 7. `RateCardController.java` (added)

**Why:** you asked to check the mappings through a controller rather than a test.

| Method | URL | Response |
|---|---|---|
| GET | `/api/v1/rate-cards/services` | Enabled services (21 with the sample data) |
| GET | `/api/v1/rate-cards/services/{serviceId}?date=YYYY-MM-DD` | Active card with slabs and rules; 404 if none. If `date` is omitted, today's date (server time zone) is used. |
| GET | `/api/v1/rate-cards/lane?origin=201301&destination=560001` | Pincode flags and zones for every partner |

- `@Validated` + `@Pattern("\\d{6}")` on the pincode parameters. `spring-boot-starter-validation` is already in `build.gradle`.
- Path is `/api/v1/...` per the planned API convention, not the old `/api/rate`.
- **Security:** not listed in `SecurityConfig`, so it falls under `anyRequest().authenticated()` and needs a JWT. It's open when `app.security.enabled=false`.

**Try:**
```bash
curl -H "Authorization: Bearer <jwt>" localhost:8080/api/v1/rate-cards/services
curl -H "Authorization: Bearer <jwt>" "localhost:8080/api/v1/rate-cards/services/1?date=2026-10-12"
curl -H "Authorization: Bearer <jwt>" "localhost:8080/api/v1/rate-cards/lane?origin=201301&destination=560001"
```

**What a successful run shows:**
- `/services/1` returns 10 slabs, and its rules end with `PEAK_SURCHARGE` (`validFrom` 2026-10-25). That confirms the V10 columns map correctly.
- `/lane` returns `directions` as a list like `["FORWARD","REVERSE","RTO"]`, and `baseCodes` as `["BASE","ODA"]`, which confirms the converters work.

**Possible failure:** if an entity column doesn't match the database, it shows up at startup or as a 500 on the first call to that endpoint.

---

## 9–10. Partner and state names on `/lane` (follow-up)

**Why:** `/lane` returned only `partnerId` and `stateId`; you asked for the names as well.

- **`repository/DeliveryPartnerRepository.java` (added):** `JpaRepository<DeliveryPartnerEntity, Long>`. There was no repository for `SO_DELIVERY_PARTNER_LIST` before. It's in the global `repository` package because the entity is global.
- **`PincodeView`:** adds `partnerName` and `stateName`. `from(...)` now takes both names.
- **`ZoneView`:** adds `partnerName`.
- **`RateCardQueryService.lane()`:** 4 queries in total, whatever the number of partners:
  1. Pincode rows for both pincodes.
  2. Zone rows for the lane.
  3. All partners (14 rows), mapped to `Map<partnerId, name>`.
  4. Only the states that appear in the pincode rows (`findAllById`), mapped to `Map<stateId, name>`.

  This avoids one name lookup per row (the N+1 problem).
- **Partner ID type:** `DeliveryPartnerEntity.id` is `Long` but the column is `INT`, so names are keyed with `id.intValue()` to match `partnerId` (`Integer`). This goes away once the entity is fixed to use `Integer`.
- **Missing names:** a name is `null` if the ID has no row (this shouldn't happen, because of the foreign keys).
- **Response shape changed:** clients reading `/lane` by position would break, but this endpoint is new and used only for inspection.

---

## 11. `/lane` grouped by partner (follow-up)

**Why:** in a flat list, each partner appeared twice: one row for the origin pincode and one for the destination. That's correct data but reads like duplicates.

**New shape:**
```json
{ "origin": "201301", "destination": "560001",
  "partners": [
    { "partnerId": 1, "partnerName": "DELHIVERY",
      "origin":      { "pincode": "201301", "cityCode": "DELHI_NCR", "stateName": "...", "pickup": true, ... },
      "destination": { "pincode": "560001", "cityCode": "BENGALURU", "stateName": "...", "delivery": true, ... },
      "zones": [ { "pricingModel": "ZONE_SLAB", "locationKey": "C" } ] } ] }
```
- **`PincodeView` / `ZoneView`:** `partnerId` and `partnerName` moved up to the new `PartnerLaneView`.
- **Missing data:** `origin` or `destination` is `null` when the partner has no row for that pincode (meaning not serviceable). `zones` is empty for hyperlocal partners, which have no zone map.
- **Order:** partners are sorted by ID (a `TreeSet` of IDs from both pincode and zone rows).
- **Bug fix:** `Set.of(origin, destination)` threw `IllegalArgumentException` when origin equalled destination (same-city lane). It's now `Set.copyOf(List.of(...))`.

---

## Other notes

- `build/claude-stage/src.tgz`: a temporary copy I made for the compile attempt. It's inside `build/` (gitignored), and `./gradlew clean` removes it.
- `.claude-tmp/` is still in the repo root. I don't have delete permission there, so please delete it yourself.
- Not changed: `DeliveryPartnerEntity` maps `id` as `Long` with `@GeneratedValue`, but V1 defines it as `INT` with no auto-increment. Fix this separately.
- `docs/RATE_CALCULATOR_GUIDE.md` is out of date with V6 (`PER_KG`/`TIERED` vs `FLAT`/`PERCENT`/`PER_UNIT`). Update it before step 4.
