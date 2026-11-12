# TableTap: implementation plan

TableTap is a multi-tenant QR menu and ordering web app. A restaurant (tenant) signs up, builds its menu, and prints one QR code per table. A customer scans the code, browses the menu, picks items with options, and places an order with no login. Staff see orders arrive live and move them through to paid.

This is a portfolio project for a fresher Java backend position. The priority is a small scope that is finished, tested, and deployed, with design decisions the author can explain in an interview.

This file is the source of truth for implementation sessions. If a session needs to depart from it, update this file in the same change and say why.

## Stack

| Concern | Choice |
|---|---|
| Language and build | Java 21, Gradle (Groovy DSL, with wrapper) |
| Framework | Spring Boot 3.5.x: Web, Data JPA, Security, Validation, Thymeleaf, Actuator |
| Database | PostgreSQL 16, Flyway migrations |
| Auth | Spring Security, JWT for staff (stored in an HttpOnly cookie) |
| QR | ZXing (`com.google.zxing:core`, `javase`) |
| QR sheet | Printable HTML page (`/admin/tables/print`), printed or saved as PDF from the browser |
| Live updates | Server-Sent Events (`SseEmitter`) |
| API docs | springdoc-openapi |
| Tests | JUnit 5, AssertJ, Testcontainers (PostgreSQL), MockMvc |
| Delivery | Dockerfile, Docker Compose, GitHub Actions |

Base package: `heeyoung.tabletap`. Package by feature, not by layer: `tenant`, `auth`, `menu`, `floor`, `order`, `kitchen`, `common`.

## Design decisions

### 1. Multi-tenancy: shared schema with a discriminator column

Every tenant-owned table has a `tenant_id` column. Entities map it with Hibernate's `@TenantId`, and a `CurrentTenantIdentifierResolver` reads the tenant from a request-scoped `TenantContext`. Hibernate then adds the tenant filter to every query and sets the column on insert, so a forgotten `WHERE` cannot leak data.

How the tenant is resolved per request:

- **Staff requests** (`/admin/**`, `/api/admin/**`): from the `tenantId` claim in the JWT.
- **Customer requests** (`/t/{qrToken}/**`, `/api/public/{qrToken}/**`): from the table that owns the QR token. Order status requests (`/api/public/orders/{publicToken}`) use the order that owns the public token.

For staff, a servlet filter sets `TenantContext` from the JWT and clears it in a `finally` block. For customers, the public controller resolves the token with plain SQL and runs the work inside `TenantContext.callAs(tenantId, ...)`. Only two endpoints need this and each takes its tenant from a different token, so an explicit call is simpler than a second filter.

Two lookups happen before the tenant is known, so they must not go through tenant-filtered entities. Do both with `JdbcTemplate`:

- Login: find the staff user by email (email is globally unique).
- QR scan: find `tenant_id` and table id by `qr_token`.

The `tenant` table itself is not tenant-scoped.

Alternative considered: schema per tenant. It gives stronger isolation but makes migrations and connection handling much more complex. Not worth it at this scale.

### 2. QR codes carry an opaque token

Each table has a random UUID `qr_token`. The QR encodes `{APP_BASE_URL}/t/{qrToken}`. Tenant ids and table numbers never appear in the URL, so nothing can be guessed. The owner can regenerate a token, which invalidates the old printed code.

### 3. Variants are option groups

A menu item has zero or more option groups (Size, Toppings, Sugar level). Each group has `min_select` and `max_select`. Each option has a `price_delta`. Examples:

- Size: min 1, max 1 (required single choice).
- Toppings: min 0, max 3 (optional multi choice).

### 4. Money is an integer

Store prices as `BIGINT` in the smallest currency unit. The tenant has a `currency` code (default `VND`). Never use floating point for money.

### 5. The server owns pricing

The order request contains only item ids, option ids, quantities, and notes. The server loads current prices, validates option rules, and computes totals. Any price sent by the client is ignored.

### 6. Orders snapshot what was sold

`order_item` and `order_item_option` copy the item name, option name, and prices at order time. Editing or deleting a menu item later does not change past orders.

### 7. Order status is one state machine

```
PLACED -> ACCEPTED -> PREPARING -> SERVED -> PAID
PLACED -> CANCELLED
ACCEPTED -> CANCELLED
```

Transitions live in one place (an enum method or a small `OrderStateMachine` class). An illegal transition returns HTTP 409.

### 8. Customers have no accounts

Placing an order returns a random `public_token`. The customer page uses it to show order status. The cart lives in the browser (`localStorage`) until the order is placed.

## Data model

All ids are `UUID`. All tables have `created_at`. Tenant-owned tables have `tenant_id UUID NOT NULL REFERENCES tenant(id)` and an index that starts with `tenant_id`.

```
tenant
  id, name, slug (unique), currency, created_at

staff_user                      (tenant-owned)
  id, tenant_id, email (unique globally), password_hash, role [OWNER|STAFF]

dining_table                    (tenant-owned)
  id, tenant_id, label, qr_token (unique), active
  unique (tenant_id, label)

category                        (tenant-owned)
  id, tenant_id, name, sort_order

menu_item                       (tenant-owned)
  id, tenant_id, category_id, name, description, base_price, image_url, available, sort_order

option_group                    (tenant-owned)
  id, tenant_id, menu_item_id, name, min_select, max_select, sort_order
  check (min_select >= 0 and max_select >= min_select)

menu_option                       (tenant-owned)
  id, tenant_id, option_group_id, name, price_delta, available, sort_order

orders                          (tenant-owned)
  id, tenant_id, dining_table_id (nullable, on delete set null), table_label,
  public_token (unique), status, total, note, created_at, updated_at

order_item                      (tenant-owned)
  id, tenant_id, order_id, position, menu_item_id (nullable, on delete set null),
  item_name, unit_price, quantity, line_total, note

order_item_option               (tenant-owned)
  id, tenant_id, order_item_id, position, group_name, option_name, price_delta
```

`unit_price` is `base_price` plus the sum of selected `price_delta` values. `line_total` is `unit_price * quantity`. `orders.total` is the sum of line totals.

## HTTP surface

JSON API under `/api`, server-rendered pages elsewhere. Errors use RFC 7807 `ProblemDetail` from a single `@RestControllerAdvice`.

**Auth**

- `POST /api/auth/register` creates a tenant and its OWNER user.
- `POST /api/auth/login` sets the JWT cookie.
- `POST /api/auth/logout`

**Admin (JWT required, tenant from token)**

- `/api/admin/categories` CRUD
- `/api/admin/items` CRUD, including nested option groups and options. `PUT` replaces the option groups as a whole, so their ids change.
- `PUT /api/admin/items/{id}/availability` with body `{ "available": false }` marks an item sold out.
- `/api/admin/tables` CRUD
- `POST /api/admin/tables/{id}/regenerate-qr`
- `GET /api/admin/tables/{id}/qr.png`
- `GET /api/admin/restaurant` returns the name, slug and currency of the logged-in restaurant.
- `GET /api/admin/orders?status=`
- `POST /api/admin/orders/{id}/transition` with body `{ "to": "ACCEPTED" }`
- `GET /api/admin/orders/stream` (SSE)

**Public (no login, tenant from QR token)**

- `GET /api/public/{qrToken}/menu` returns restaurant name, table label, categories, items, option groups.
- `POST /api/public/{qrToken}/orders` places an order.
- `GET /api/public/orders/{publicToken}` returns order status.

**Pages (Thymeleaf)**

- `/t/{qrToken}` customer menu and cart, mobile-first.
- `/t/{qrToken}/orders/{publicToken}` order status.
- `/login`, `/register`
- `/admin/menu`, `/admin/tables`, `/admin/tables/print`, `/admin/kitchen`

## Phases

Finish and verify each phase before starting the next. Every phase ends with passing tests and a commit.

### Phase 1: skeleton

- Spring Boot project with the Gradle wrapper.
- `compose.yaml` with PostgreSQL for local development.
- Profiles: `dev` (local defaults) and `prod` (everything from environment variables).
- Flyway baseline migration creating `tenant`.
- `.gitattributes` forcing LF for `gradlew` and `*.sh`. `.gitignore`.
- GitHub Actions workflow running `./gradlew build`.
- A Testcontainers base class for integration tests.

Done when: the app starts against the Compose database, `/actuator/health` returns UP, and CI is green.

### Phase 2: tenancy and auth

- Migrations for `staff_user`.
- `TenantContext`, the tenant resolver, and the request filter.
- Register, login, logout. BCrypt password hashes. JWT with `sub`, `tenantId`, `role`.
- Security config: `/api/admin/**` and `/admin/**` need authentication, public routes are open.

Done when: an integration test registers two tenants and proves a user of tenant A gets nothing from tenant B. Add this isolation check for every new tenant-owned resource in later phases.

### Phase 3: menu management

- Migrations and entities for `category`, `menu_item`, `option_group`, `menu_option` (`option` is an SQL keyword).
- Admin CRUD with bean validation (non-negative prices, `max_select >= min_select`).
- Login and register pages, and the admin menu page (Thymeleaf shell, vanilla JS calling the JSON API).

Done when: an owner can build a full menu with option groups through the UI, and tests cover validation failures and tenant isolation.

### Phase 4: tables and QR

- Migration and entity for `dining_table`.
- Table CRUD, token regeneration.
- PNG endpoint via ZXing. Printable sheet with one labelled QR per active table. This is an HTML print page, not a generated PDF, because the built-in PDF fonts cannot render Vietnamese names.
- `TableLookup` resolves a QR token to its tenant and table with plain SQL. `/t/{qrToken}` shows a placeholder page until phase 5.
- `APP_BASE_URL` config property used to build the encoded URL.

Done when: scanning a printed code with a phone opens `/t/{qrToken}`, and a regenerated token makes the old URL return 404.

### Phase 5: customer ordering

- Tenant resolved from the QR token (see design decision 1).
- Public menu endpoint (only `available` items and options, and no item whose required option group has nothing left). Order pricing validates against this same `CustomerMenu`, so customers can only order what the menu offered. A rule violation returns HTTP 422 with a message for the customer.
- Order placement with server-side validation and pricing.
- Customer menu page with a `localStorage` cart, and the order status page.

Done when: unit tests cover pricing and option rules (missing required option, too many options, unavailable item, option from another item, quantity below 1), and an integration test places an order end to end.

### Phase 6: kitchen dashboard

- Order list and transition endpoint backed by the state machine.
- SSE stream per tenant. Publish on order placed and on status change.
- Kitchen page that updates live, with a reload fallback on reconnect.
- Events are sent after the transaction commits, with a heartbeat comment every 25 seconds so proxies keep the stream open. The customer status page polls every 5 seconds instead of holding a stream, which keeps one open connection per kitchen screen rather than per diner.

Done when: placing an order in one browser shows it on the kitchen page in another without refresh, tests cover every legal and illegal transition, and a tenant only receives its own events.

### Phase 7: polish and deploy

- Seed data for two demo restaurants behind a `demo` profile.
- OpenAPI docs at `/swagger-ui.html`.
- Multi-stage `Dockerfile` and a `compose.prod.yaml` (app, PostgreSQL, Caddy for HTTPS).
- Deploy to one AWS EC2 `t3.micro` running Docker.
- README with a feature list, ER diagram, architecture notes, screenshots, demo link and demo login.

Done when: the demo URL works from a phone on mobile data, and a fresh clone runs with `docker compose up`.

## Development and deployment

Development happens on Windows. Production is Linux on AWS. To keep them compatible:

- No hardcoded file paths or backslashes. Use config values and relative paths.
- Keep file name casing consistent. Linux is case-sensitive, so `Menu.html` and `menu.html` differ there.
- Secrets come from environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `APP_BASE_URL`. Never commit them.
- The Docker image is the unit of deployment. Test the image locally before deploying.
- On AWS, open only ports 22, 80, and 443. PostgreSQL is reachable only inside the Compose network. Set a billing alarm.

## Conventions

- Controllers take and return DTOs (Java records). Entities never leave the service layer.
- Constructor injection only.
- `@Transactional` on service methods, not controllers.
- Schema changes only through new Flyway migrations. Never edit an applied migration. `spring.jpa.hibernate.ddl-auto=validate`.
- Tests assert behaviour through the HTTP API or a service's public methods, not internals.
- Integration tests extend `IntegrationTest`. They use Testcontainers when Docker is running. Without Docker, set `TEST_DATABASE_URL` (for example `jdbc:postgresql://localhost:5432/tabletap`, user and password `tabletap`) to run them against an existing database. With neither, they are skipped. Because the database can outlive a test run, every test creates its own uniquely named data.
- Small commits, one logical change each, with clear messages.

## Known limitations

These are accepted for this scope. List them in the README.

- Anyone with a table's URL can place an order from anywhere. Real products add a session code or staff confirmation. The `ACCEPTED` step is the mitigation here.
- SSE emitters are held in memory, so the app runs as a single instance.
- No rate limiting on public endpoints.

## Out of scope

Online payment, customer accounts, inventory, analytics, multiple branches per tenant, image upload (use `image_url`), and internationalisation.
