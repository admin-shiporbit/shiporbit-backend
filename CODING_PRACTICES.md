# ShipOrbit Backend Coding Practices

This guide defines the default engineering standards for this repository. Apply it to all new code and improve existing code toward these standards when touching it. Avoid unrelated rewrites in feature pull requests.

## 1. Definition of done

A change is complete only when:

- The behavior is implemented in the correct layer.
- Inputs are validated and failures return a safe, consistent API response.
- Unit tests cover business rules and edge cases; integration tests cover important database, security, and HTTP boundaries.
- `./gradlew clean test` passes locally.
- No credentials, tokens, customer data, or generated files are committed.
- Configuration and API documentation are updated when behavior changes.
- The diff contains no unrelated formatting or refactoring.

## 2. Repository architecture

Keep dependencies flowing inward through the existing layers:

```text
HTTP request
  -> controller (HTTP concerns and validation)
  -> service (use-case orchestration and transactions)
  -> domain/rate strategy (business calculations)
  -> repository or external API client (I/O)
```

Package responsibilities:

- `controller`: routes, status codes, request validation, and DTOs only. Do not put pricing, authentication, or persistence logic here.
- `service`: application use cases, authorization decisions, transaction boundaries, and mapping between entities and DTOs.
- `rate`: courier-independent rating rules and partner strategies.
- `rate.<partner>`: partner-specific request mapping, response mapping, and API behavior.
- `repository`: Spring Data query definitions only. Avoid business decisions in default methods or custom queries.
- `entity`: persistence mapping only. Never expose an entity directly from a controller.
- `dto` / `jwt`: API contracts. Prefer immutable Java records for request and response objects.
- `security`, `interceptor`, and configuration classes: cross-cutting infrastructure.
- `exception`: domain/application exceptions and the global HTTP error mapping.

When a feature becomes large, prefer a feature package containing its controller, service, DTOs, and persistence code rather than adding ever-growing global packages. Keep shared code genuinely shared; do not create a `util` dumping ground.

## 3. Java style and design

- Target Java 21, as configured in `build.gradle`, and use the Gradle wrapper (`./gradlew`).
- Use four spaces, braces on the same line, one public top-level type per file, and a final newline.
- Use descriptive names. Classes are nouns (`RateCalculator`), methods express actions (`calculateForAllServiceablePartners`), and booleans read as predicates (`isServiceable`).
- Prefer constructor injection. Dependencies should be `private final`; do not use field injection.
- Prefer small, cohesive methods. Extract code when it gives a business concept a name, not merely to reduce line count.
- Prefer immutable values, records, unmodifiable collections, and side-effect-free calculations.
- Return empty collections instead of `null`. Use `Optional` for a possibly absent return value, not for fields or parameters.
- Use enums for closed sets such as risk type, role, and status. Parse external strings once at the API boundary.
- Do not catch `Exception` or `RuntimeException` unless the boundary has a deliberate recovery policy. Never silently swallow a failure.
- Avoid static mutable state and magic values. Put true domain constants near the owning domain type; put deploy-specific values in configuration.
- Use Lombok selectively. `@Getter`, `@Builder`, and constructors can remove noise; avoid `@Data` on JPA entities because generated equality, string output, and setters can expose relationships or secrets.
- Add comments for intent, constraints, or a non-obvious trade-off. Do not narrate code that is already clear.

### Value types

- Use `BigDecimal` for money, rates, taxes, COD amounts, and other exact decimal calculations. Never use `double`/`Double` for new monetary code.
- State the scale and rounding rule at the calculation boundary, for example `setScale(2, RoundingMode.HALF_UP)`. Do not round intermediate values unless the business rule requires it.
- Use `Instant` for machine timestamps and store them in UTC. Use `LocalDate` for date-only business values. Convert to a user timezone only at an API/UI boundary.
- Prefer domain value objects for values with rules (pincode, weight, dimensions, partner code) once those rules are reused.

## 4. Spring and API conventions

### Controllers and contracts

- Version public endpoints under `/api/v1`.
- Use nouns for resources and HTTP methods for actions. Avoid verbs in paths unless the operation is not naturally resource-oriented.
- Accept and return DTOs, never JPA entities or partner SDK objects.
- Apply `@Valid` at the controller boundary and Jakarta validation annotations on request DTOs. Validate nested values with `@Valid`.
- Normalize case and whitespace once, close to input handling. Services should receive canonical values.
- Use the narrowest correct status: `201 Created` for creation, `204 No Content` for successful deletion with no body, `400` for malformed input, `401` for missing/invalid authentication, `403` for insufficient permission, `404` for absent resources, `409` for state conflicts, and `502/503/504` for upstream failures as appropriate.
- Treat DTO evolution as API evolution. Do not rename/remove response fields or change their meaning without a versioning or migration decision.
- Add pagination to endpoints that can grow without a small hard limit.

### Services and transactions

- Put transaction boundaries on service methods. Prefer Spring's `org.springframework.transaction.annotation.Transactional` consistently.
- Use `@Transactional(readOnly = true)` for database-backed reads and a normal transaction for writes.
- Do not hold a database transaction open while calling Delhivery or another remote service. Split persistence and network operations when needed.
- Make operations that may be retried idempotent, especially shipment creation, wallet changes, webhooks, and partner bookings.

### Persistence

- Preserve the repository's `SO_` database naming convention and explicit constraint names.
- Keep entity column constraints aligned with SQL schema and request validation.
- Avoid unbounded repository reads and N+1 queries. Use pagination, projections, entity graphs, or explicit fetch queries as appropriate.
- Default relationships to lazy loading and map to DTOs inside the service transaction.
- Implement entity equality using a stable identity deliberately; never include lazy relationships or mutable fields.
- Use a migration tool such as Flyway or Liquibase before multiple deployed environments depend on the schema. Once adopted, migrations are append-only; never edit an already deployed migration.

## 5. Errors and logging

- Throw specific exceptions that describe the failed business condition. Map them centrally in `GlobalExceptionHandler`.
- Keep `ErrorResponse` stable and include a correlation/error ID, status, safe message, and timestamp.
- Never return `ex.getMessage()` for an unexpected exception. Log the exception server-side with its error ID and return a generic message such as `An unexpected error occurred`.
- Validation errors should be deterministic and field-oriented. Consider a structured list instead of a comma-joined string as the API matures.
- Log with placeholders (`LOGGER.info("Partner {} selected", code)`), not string concatenation.
- `INFO`: significant lifecycle or business events; `DEBUG`: diagnostic detail; `WARN`: recoverable abnormal conditions; `ERROR`: failed operations requiring attention.
- Never log passwords, JWTs, API keys, authorization headers, personal addresses, or full partner payloads. Mask email, phone, and pincode data where logs do not require the full value.
- Include useful context such as correlation ID, shipment/order ID, and partner code. Do not log the same exception at every layer.

## 6. Security and configuration

- Secrets must come from environment variables or a secret manager. Never add a real secret or a production-like fallback to `application.yaml`, tests, SQL, Docker Compose, or documentation.
- Separate configuration by profile (`application-dev.yaml`, `application-test.yaml`, `application-prod.yaml`). Production must fail fast when required secrets are absent.
- The security bypass (`app.security.enabled=false`) is local-development-only. Production configuration must enable authentication and must not create a fixed development principal.
- Keep authorization deny-by-default: explicitly permit only health and authentication endpoints that must be public.
- Validate ownership/tenant access in the service layer; knowing a resource UUID must never grant access.
- Hash passwords only with the configured adaptive password encoder. Never store, echo, or log raw passwords.
- Keep Actuator exposure minimal and protect non-public endpoints.
- Set timeouts on every outbound HTTP call. Retry only transient, idempotent operations with bounded exponential backoff; use circuit breaking when an integration becomes business-critical.
- Validate partner responses and translate partner-specific failures into internal exceptions without leaking tokens or sensitive response bodies.

## 7. Testing strategy

Use the smallest useful test:

- Plain JUnit unit test for calculations, normalization, mapping, and service decisions.
- Mockito only at actual boundaries; prefer small fakes for simple strategy interfaces, as used in `RateCalculatorTest`.
- `@WebMvcTest` for routes, validation, serialization, security rules, status codes, and error bodies.
- `@DataJpaTest` for repository queries and JPA mappings.
- `@SpringBootTest` sparingly for critical end-to-end application wiring.
- MockWebServer/WireMock-style tests for Delhivery success, timeout, malformed response, authentication failure, and retry behavior. Tests must never call the live partner API.

Test naming should describe behavior, for example:

```java
@Test
void rejectsQuoteWhenPartnerIsNotServiceable() { }
```

Every bug fix needs a regression test that fails before the fix. Cover the happy path, boundary values, invalid input, authorization, absent data, and upstream failure. Tests must be deterministic: no dependence on execution order, wall-clock time, the developer database, or the network.

Run before opening a pull request:

```bash
./gradlew clean test
```

For meaningful infrastructure or persistence changes, also run the application against local PostgreSQL and exercise the affected endpoint.

## 8. Git and review discipline

- Branch from an up-to-date main branch and keep commits focused and buildable.
- Use an imperative commit subject, for example `Add Delhivery timeout handling`.
- Keep pull requests small enough to review. Separate mechanical refactoring from behavior changes.
- In the pull request, explain the problem, solution, API/schema/config impact, tests performed, and rollback or compatibility concerns.
- Review migrations, authentication/authorization, money calculations, and external API behavior as high-risk areas.
- Do not commit IDE state, logs, build output, local profile files, `.env` files, or copied API responses.

## 9. Repository-specific improvement checklist

Apply these priorities when the relevant code is next changed:

1. Reconcile documentation with `build.gradle`; the build currently declares Spring Boot 4.1.0 while the README describes Spring Boot 3.x.
2. Replace monetary `Double` fields and calculations in the rate flow with `BigDecimal`, with explicit rounding tests.
3. Remove development credentials and secret-like JWT defaults from committed runtime configuration; use profile-specific safe configuration and required environment variables.
4. Ensure the generic exception handler returns a safe fixed message rather than exposing an unexpected exception message.
5. Standardize formatting and naming (including typos such as security/persist identifiers) with an automated formatter and static analysis in Gradle/CI.
6. Expand controller, security, repository, and partner-client integration tests; the current suite is weighted toward rate calculation unit tests.
7. Introduce database migrations before deploying schema changes across shared environments.

## 10. Quick pull-request checklist

- [ ] Correct layer and package
- [ ] Constructor injection and immutable dependencies
- [ ] DTO validation and safe API errors
- [ ] Authorization and ownership checked
- [ ] Exact monetary arithmetic and explicit rounding
- [ ] No secrets or sensitive logging
- [ ] Timeouts and failure handling for external calls
- [ ] Unit/integration tests added as appropriate
- [ ] `./gradlew clean test` passes
- [ ] API, configuration, and schema documentation updated
- [ ] Focused diff with no generated or unrelated files
