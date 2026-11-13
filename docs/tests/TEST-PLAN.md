# TableTap test plan before deploying

Written 2026-11-13 from a read of the source and the existing tests. Nothing was run to produce it, so every gap below is inferred from the code. Tick a box when its test exists and passes.

## What is already covered

118 tests across 16 classes in `src/test/java`, mostly full HTTP tests against a real PostgreSQL. Auth and JWT handling, tenant isolation, menu, tables and QR codes, ordering and pricing, the status state machine and the kitchen stream are covered on the happy path and the main rejections. None of it needs rewriting.

Three areas have no tests: the browser code, the production configuration, and real concurrency. P0 fills those.

## Expected failures

Five items are predictions that the current code is wrong. Each needs a code fix as well as a test, handled as its own small change once the test confirms it.

| Prediction | Where | Test |
| --- | --- | --- |
| The placeholder `JWT_SECRET` from `deploy/tabletap.env.example` is long enough to pass the length check. **Confirmed and fixed.** | `JwtProperties` | P0.1 |
| ~~More than three parallel registrations with one restaurant name fail with a misleading 409~~ Wrong. Ten at once all registered with distinct slugs. | `AuthService.register` | P0.4 |
| Deleting a category while an item is created in it returns 500. **Confirmed and fixed**, the loser of the race now gets a 409. | `ApiExceptionHandler` rethrows the foreign-key error | P0.4 |
| A signed token with a missing or malformed `tenantId` or `role` claim returns 500 | `TenantContextFilter`, `SecurityConfig.roleConverter` | P1 auth |
| A token is still accepted after logout until it expires | stateless JWT | P1 auth |

## P0. Deploy blockers

### P0.1 Production profile (`ProdProfileTest`, new)

Every current test runs on the `dev` profile, so nothing checks the settings that get deployed.

- [x] Login cookie carries `Secure` under `prod`.
- [x] A request with `X-Forwarded-Proto: https` redirects `/admin/menu` to an `https` login URL.
- [x] The app refuses to start with a missing `JWT_SECRET`.
- [x] The app refuses to start with a `JWT_SECRET` shorter than 32 bytes.
- [x] The app refuses to start with the placeholder secret from `deploy/tabletap.env.example`.
- [x] Only `/actuator/health` is reachable, and it shows no details.

### P0.2 Post-deploy check (on the server)

The project doesn't use Docker, so the old container smoke job is gone. These checks run by hand on the real server after a deploy (GUIDE section 6, step 11).

- [ ] `https://your-domain/actuator/health` returns `UP` through Caddy, and `http://` redirects to `https://`.
- [ ] Through Caddy: register, create a table, place an order, read it back.
- [ ] The kitchen board shows the order within two seconds, which proves Caddy does not buffer SSE.
- [ ] `systemctl status tabletap` shows the service running, with no restarts, after that run.

### P0.3 Migrations

Flyway from an empty database followed by Hibernate `validate` is already proven by every integration test.

- [x] Upgrade path: migrate to V5, insert an order, migrate to V6, read the order back.
- [x] Restart on an already-migrated database with `prod,demo` creates no duplicate demo data. `DemoDataTest` covers `dev` only.

### P0.4 Real concurrency (`ConcurrencyTest`, new)

The one existing test uses a stale entity in a single thread. None fire parallel HTTP requests.

- [x] Ten parallel transitions on one order, half to `ACCEPTED` and half to `CANCELLED`. Exactly one 200, the rest 409, no 500.
- [x] Ten parallel registrations with one email. One 201, nine 409.
- [x] Ten parallel registrations with one restaurant name. All 201 with distinct slugs.
- [x] Parallel table creates with one label. One 201, the rest 409.
- [x] Delete a category while creating an item in it. Never a 500.
- [x] Place an order while its item is being updated. Either 201 priced from one consistent menu, or 422. Never a 500 or a mixed price.

### P0.5 Browser journeys (Playwright for Java, new)

About 800 lines of JavaScript have no tests. Playwright for Java keeps everything under `./gradlew test` with the same database.

- [x] Customer on a phone viewport: open the scan URL, pick a required option, add to cart, reload and keep the cart, submit, land on the status page.
- [x] Required-option rule: the add button stays disabled until the minimum is chosen, and the maximum cannot be exceeded.
- [x] Stale cart: the owner marks an item sold out after it is in the cart. Submit shows the server message and keeps the cart.
- [x] Kitchen board: a new order appears without a refresh, each button moves it one lane, and a 409 caused by a second screen shows a message.
- [x] Status page changes after the kitchen moves the order.
- [x] Owner setup: register, build an item with option groups, create a table, open the print sheet.
- [x] Expired login: an admin page call that returns 401 sends the browser to `/login`.
- [x] Markup in names: a dish and a note containing `<img onerror>` render as text on the customer, order and kitchen pages.

## P1. Before real customers

### Auth and sessions

- [ ] A token is still accepted after logout until it expires. Written as a test so the behavior is a recorded decision.
- [ ] A valid token whose user row is gone gets 404 from `/api/admin/me`, not 500.
- [ ] A correctly signed token with a missing or malformed `tenantId` or `role` claim gets 401, not 500.
- [ ] A token in an `Authorization: Bearer` header is ignored, since only the cookie is read.
- [ ] Email with surrounding spaces.
- [ ] Password boundaries: 7 and 8 characters, 72 and 73 bytes of non-ASCII text.

### Tenant isolation as a matrix

- [ ] One parameterized test calls every `/api/admin/**` route that takes an id with another tenant's cookie. Each returns 404. Routes are discovered from the OpenAPI document so a new endpoint cannot be forgotten.

### Public endpoints

- [ ] An order's public token returns only its own restaurant's data. The response is checked field by field for anything a customer should not see. It currently includes the internal order `id`.
- [ ] `/t/{anything}/orders/{validToken}` with a hostile first segment renders escaped.
- [ ] A regenerated or deactivated QR code stops new orders, and an existing order's status page still works.
- [ ] Body limits: note of 500 and 501 characters, 50 and 51 lines, 30 and 31 options, a null line, quantity missing, 0, 1, 99, 100.
- [ ] Malformed JSON, wrong content type and a non-UUID path each return 400 or 415 with a problem-detail body.

### Pricing and money

- [ ] Maximum price times maximum quantity times 50 lines stays inside `long` and returns 201.
- [ ] An item with a zero price.
- [ ] An option group whose available options drop below `maxSelect`.
- [ ] Option ids from a group that was replaced by an item update return 422.
- [ ] The total of a multi-line order with repeated items matches a hand-computed number.

### Orders and history

- [ ] Deleting a table keeps its past orders and their label. Check first whether `pastOrdersKeepTheNamesAndPricesTheyWereSoldAt` already covers the table.
- [ ] With 201 open orders the list returns the newest 200, oldest first.
- [ ] `?status=BOGUS` on the list returns 400.
- [ ] Every terminal state rejects every move over HTTP, not only in the enum test.

### Kitchen stream

- [ ] A closed client is removed from the subscriber set after the next heartbeat.
- [ ] A rolled-back order placement publishes nothing.
- [ ] Two screens for one restaurant both receive each event.
- [ ] After a reconnect the board reloads and shows orders placed while it was away.

### Menu and tables

- [ ] `minSelect` greater than the available options hides the item, and restoring one option brings it back.
- [ ] `imageUrl` with a `javascript:` or `data:` value. It is set as an `img src` in `customer.js`, which browsers do not execute, so this is a characterization test.
- [ ] Label of 40 and 41 characters.
- [ ] Sort order ties are broken by name.
- [ ] A category rename shows on the customer menu.

## P2. Operational checks, run once by hand before launch

- [ ] Backup and restore drill. Take a dump with the command in `docs/GUIDE.md`, restore it into a fresh database, start the app on it.
- [ ] Light load on the real server size. 20 orders per second for five minutes with 20 open kitchen streams, plus 5 logins per second. bcrypt on a `t3.micro` is the likely CPU limit. Record p95 latency and memory.
- [ ] Stalled kitchen screen. The README lists this as a known limitation. Measure how long one frozen stream delays order placement.
- [ ] Real phones. Scan a printed code on one iPhone and one Android, including private mode where `localStorage` may be blocked.
- [ ] Response headers on the live domain: HSTS, `X-Content-Type-Options`, frame options and the cookie flags.

## Tooling

- [ ] Add JaCoCo and publish the report in CI. Use it to find untested branches, not as a pass threshold.
- [x] Tag browser tests so `./gradlew test` stays fast and CI runs everything.
- [ ] Keep the rule that CI fails when the database is missing.

## Decisions

- **Playwright for Java, not a Node test runner.** It avoids a second toolchain.
- **No coverage threshold.** A percentage gate invites tests written to satisfy the number.
- **Rate limiting and the open table URL are out of scope.** Both are known limitations in the README. The plan measures them and does not test for a fix.
