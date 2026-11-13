# TableTap

QR menus and ordering for restaurants. A restaurant signs up, builds its menu and prints one QR code per table. A customer scans the code, picks dishes and options on their phone and orders without an account. The kitchen sees the order appear live and moves it through to paid.

**Demo:** _add the URL after deploying_ · log in as `owner@pho.demo` / `tabletap-demo` (a second restaurant: `owner@banhmi.demo`, same password). API docs are at `/swagger-ui.html`.

**Guide:** [docs/GUIDE.md](docs/GUIDE.md) has the commands for running locally, the page and API map, connecting to the database, and deploying and operating the server.

<!-- Screenshots: add images of the customer menu (phone), the order status page and the kitchen board to docs/ and link them here. -->

## Features

- **Restaurants (tenants)** register an owner account and log in. Every restaurant sees only its own data.
- **Menu management.** Categories, items, prices, sold-out switch, and option groups such as "Size, choose 1" or "Toppings, up to 3" with a price for each option.
- **Tables and QR codes.** One code per table, a printable sheet, and a button to replace a code so an old printout stops working.
- **Customer ordering** on a phone-first page. The cart is kept in the browser until the order is sent. The server checks every choice against the menu and computes the price itself.
- **Order status page** for the customer, updated every few seconds.
- **Live kitchen board.** New orders appear without a refresh (Server-Sent Events). Staff move each order through New, Accepted, Cooking, Served and Paid, or cancel it.

## Stack

Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation, Thymeleaf), PostgreSQL 16 with Flyway, JWT in an HttpOnly cookie, ZXing for QR codes, springdoc-openapi, plain JavaScript on the front end. Tests use JUnit 5, AssertJ and Playwright. In production it runs as a systemd service behind Caddy. No Docker anywhere: development, CI and the server all use Java 21 and PostgreSQL installed directly.

## Architecture

The code is grouped by feature under `heeyoung.tabletap`: `tenant`, `auth`, `menu`, `floor` (tables and QR codes), `order`, `kitchen`, `demo` and `common`. Controllers take and return records. Entities stay inside services.

```mermaid
flowchart LR
    phone["Customer phone"] -- "/t/{qrToken}<br/>/api/public/**" --> app
    kitchen["Kitchen screen"] -- "/admin/**, /api/admin/**<br/>SSE stream" --> app
    app["Spring Boot app"] --> db[("PostgreSQL")]
    caddy["Caddy (HTTPS)"] --- app
```

Design decisions worth knowing:

1. **One database, a `tenant_id` on every row.** Entities carry Hibernate's `@TenantId`, so Hibernate adds the tenant filter to every query and sets the column on insert. A forgotten `WHERE` cannot leak another restaurant's data. Staff requests get the tenant from the JWT. Customer requests get it from the QR token or the order's public token, looked up with plain SQL because the tenant is not known yet. A query that runs with no tenant matches nothing.
2. **QR codes carry a random token**, never a restaurant or table id, so nothing in the URL can be guessed.
3. **Money is a whole number** in the smallest currency unit (`BIGINT`). No floating point.
4. **The server owns pricing.** An order request carries only item ids, option ids, quantities and notes. `OrderPricing` checks it against `CustomerMenu`, the same view of available items the customer was shown, and computes every price. Prices sent by a client are ignored.
5. **Orders copy what was sold.** Item names, option names, prices and the table label are stored on the order, so editing the menu or deleting a table never changes a past order.
6. **Order status is one state machine** in `OrderStatus`. An illegal move returns HTTP 409.
7. **Live updates are sent after commit**, so the kitchen never sees an order that was rolled back. Each restaurant's screens receive only that restaurant's events.

### Data model

```mermaid
erDiagram
    tenant ||--o{ staff_user : employs
    tenant ||--o{ category : has
    tenant ||--o{ dining_table : has
    category ||--o{ menu_item : contains
    menu_item ||--o{ option_group : offers
    option_group ||--o{ menu_option : lists
    dining_table |o--o{ orders : "placed at"
    orders ||--o{ order_item : contains
    order_item ||--o{ order_item_option : "chosen with"
    menu_item |o--o{ order_item : "sold as"

    tenant { uuid id string name string slug string currency }
    staff_user { uuid id string email string password_hash string role }
    dining_table { uuid id string label uuid qr_token bool active }
    menu_item { uuid id string name bigint base_price bool available }
    option_group { uuid id string name int min_select int max_select }
    menu_option { uuid id string name bigint price_delta bool available }
    orders { uuid id string table_label uuid public_token string status bigint total }
    order_item { uuid id string item_name bigint unit_price int quantity bigint line_total }
    order_item_option { uuid id string group_name string option_name bigint price_delta }
```

Every table except `tenant` also has `tenant_id`.

## Run it

You need Java 21 and a local PostgreSQL. Create the user and databases once, as the PostgreSQL admin (`psql -U postgres`):

```sql
create role tabletap login password 'tabletap';
create database tabletap owner tabletap;
create database tabletap_test owner tabletap;
```

Then start the app:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev,demo'
```

Open http://localhost:8080 and log in as `owner@pho.demo` / `tabletap-demo`. Each demo table's QR code opens its customer page.

To scan codes with a phone on the same network, set `APP_BASE_URL=http://<your computer's LAN IP>:8080` before starting.

## Tests

```bash
./gradlew test
```

Integration tests need a PostgreSQL database, named by `TEST_DATABASE_URL`. Use the `tabletap_test` database created above, so tests never write into your development data:

```bash
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/tabletap_test ./gradlew test
```

Without `TEST_DATABASE_URL` the database tests are skipped locally. CI fails instead of skipping.

`./gradlew build` also runs the browser tests (`./gradlew browserTest`). Set `BROWSER_CHANNEL=chrome` or `msedge` to use a browser you already have. Without it, Playwright downloads its own Chromium on first run.

## Deploy

The production setup is one small Linux server with everything installed directly, no Docker: PostgreSQL 16 (listening on localhost only), the app run by systemd, and Caddy, which gets an HTTPS certificate for your domain. The files are in `deploy/`.

1. Create the server (for example AWS EC2 with Ubuntu 24.04). Open only ports 22, 80 and 443. Set a billing alarm.
2. Add 1 GB of swap so the Gradle build fits in memory.
3. Install `openjdk-21-jdk-headless`, `postgresql` and Caddy, and create the `tabletap` database and user.
4. Point your domain's DNS A record at the server's public IP.
5. Clone the repository, build with `./gradlew bootJar`, and copy the jar to `/opt/tabletap/`.
6. Copy `deploy/tabletap.env.example` to `/etc/tabletap/tabletap.env` and fill it in.
7. Install `deploy/tabletap.service` and `deploy/Caddyfile`, then start both.

[docs/GUIDE.md](docs/GUIDE.md#6-deploy-to-a-server) has every command.

Settings are read from environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `APP_BASE_URL`. Never commit them.

## Known limitations

- Anyone with a table's URL can order from anywhere. Real products add a session code or staff confirmation. The Accepted step is the mitigation here.
- Live updates are held in the app's memory, so it runs as a single instance.
- No rate limiting on the public endpoints.
- Kitchen events are written on the request thread after commit, so a kitchen screen on a stalled connection can slow down the request that placed or moved an order.
- The kitchen list shows the newest 200 open orders. Orders that are served but never marked paid stay open.
- No online payment, customer accounts, inventory, image upload (items take an image URL) or translations.
