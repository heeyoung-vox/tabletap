# TableTap guide

Day-to-day reference: run it, find your way around, reach the database, and deploy and operate it. The [README](../README.md) has the overview and design decisions. This page has the commands.

## Contents

1. [Project map](#1-project-map)
2. [Run locally](#2-run-locally)
3. [Navigate the app](#3-navigate-the-app)
4. [Database](#4-database)
5. [Tests](#5-tests)
6. [Deploy to a server](#6-deploy-to-a-server)
7. [Operate the server](#7-operate-the-server)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Project map

| Path | What it holds |
|---|---|
| `src/main/java/heeyoung/tabletap/` | Java code, one package per feature: `tenant`, `auth`, `menu`, `floor` (tables, QR), `order`, `kitchen`, `demo`, `common`, `web` (page routes) |
| `src/main/resources/application*.yml` | Settings. `application.yml` is shared, `application-dev.yml` and `application-prod.yml` override it |
| `src/main/resources/db/migration/` | Flyway SQL migrations, run automatically on startup (`V1__…` to `V6__…`) |
| `src/main/resources/templates/` | Thymeleaf pages (HTML shells) |
| `src/main/resources/static/js/`, `static/css/` | Front-end JavaScript and CSS |
| `src/test/java/` | Tests (JUnit 5, AssertJ, Playwright for the browser journeys) |
| `deploy/tabletap.service` | systemd unit that runs the app on the server |
| `deploy/tabletap.env.example` | Template for the server's settings file, `/etc/tabletap/tabletap.env` |
| `deploy/Caddyfile` | Reverse proxy and automatic HTTPS for the production domain |
| `.github/workflows/ci.yml` | CI on every push to `main` and every pull request: `./gradlew build` against the runner's own PostgreSQL |

The project doesn't use Docker. Development, CI and the server all run Java 21 and PostgreSQL directly.

## 2. Run locally

You need Java 21 and PostgreSQL installed on your machine. The project uses the Gradle wrapper, so you don't install Gradle.

### Set up the database once

Open a shell as the PostgreSQL admin (`psql -U postgres`) and run:

```sql
create role tabletap login password 'tabletap';
create database tabletap owner tabletap;
create database tabletap_test owner tabletap;
```

`tabletap` holds your development data. `tabletap_test` is for the tests (see [section 5](#5-tests)).

### Start the app

From the project root:

```bash
# macOS / Linux
./gradlew bootRun --args='--spring.profiles.active=dev,demo'

# Windows
gradlew.bat bootRun --args="--spring.profiles.active=dev,demo"
```

The `dev` profile connects to `localhost:5432` with user and password `tabletap` (see [section 4](#4-database)).

- Open http://localhost:8080
- Log in as `owner@pho.demo` / `tabletap-demo`. A second restaurant is `owner@banhmi.demo` with the same password.
- Each demo table has a QR code that opens its customer page. The demo data comes from the `demo` profile.

Drop `demo` from the profile list if you want an empty database with no demo restaurants.

### Scanning QR codes with a phone

A phone can't reach `localhost`. Find your computer's LAN IP, then start the app with that address as the base URL:

```bash
# macOS / Linux
APP_BASE_URL=http://192.168.1.20:8080 ./gradlew bootRun --args='--spring.profiles.active=dev,demo'

# Windows (PowerShell)
$env:APP_BASE_URL="http://192.168.1.20:8080"; gradlew.bat bootRun --args="--spring.profiles.active=dev,demo"
```

Replace `192.168.1.20` with your address. QR codes are built from this base URL, so codes generated before the change keep the old one. Regenerate them from the tables page.

### Settings in dev

| Setting | Dev value | Where it comes from |
|---|---|---|
| Database URL | `jdbc:postgresql://localhost:5432/tabletap` | `application-dev.yml` |
| Database user / password | `tabletap` / `tabletap` | `application-dev.yml` |
| JWT secret | a fixed dev-only string | `application-dev.yml`. Never use it in production |
| Cookie `Secure` flag | off (plain HTTP) | `application-dev.yml` |
| Base URL for QR codes | `http://localhost:8080` | `APP_BASE_URL`, default in `application-dev.yml` |

## 3. Navigate the app

### Pages

| URL | Who | What it's for |
|---|---|---|
| `/` | staff | Redirects to `/admin/menu` |
| `/register` | anyone | Create a restaurant and its owner account |
| `/login` | anyone | Staff login. Sets an HttpOnly JWT cookie valid for 8 hours |
| `/admin/menu` | staff, logged in | Categories, items, prices, sold-out switch, option groups |
| `/admin/tables` | staff, logged in | Add tables, see and replace QR codes, deactivate tables |
| `/admin/tables/print` | staff, logged in | Printable sheet of all QR codes (print, or save as PDF from the browser) |
| `/admin/kitchen` | staff, logged in | Live order board. Updates in real time over Server-Sent Events |
| `/t/{qrToken}` | customer, no login | Menu for one table. This is what the QR code opens |
| `/t/{qrToken}/orders/{publicToken}` | customer, no login | Order status page, refreshed every few seconds |
| `/swagger-ui.html` | anyone | Interactive API docs (springdoc) |
| `/actuator/health` | anyone | Health check. Returns `UP` when the app and database are reachable |

Staff pages need a login. Anything under `/admin/**` or `/api/admin/**` without a valid cookie is refused.

### API routes

| Prefix | Auth | Purpose |
|---|---|---|
| `/api/auth/**` | none | `POST /register`, `POST /login`, `POST /logout` |
| `/api/admin/me`, `/api/admin/staff` | staff | Current user, staff list |
| `/api/admin/restaurant` | staff | Restaurant details |
| `/api/admin/categories`, `/api/admin/items` | staff | Menu management |
| `/api/admin/tables` | staff | Table management. `POST /{id}/regenerate-qr` replaces a QR code. `GET /{id}/qr.png` returns the image |
| `/api/admin/orders` | staff | Kitchen list. `POST /{id}/transition` moves an order along. `GET /stream` is the SSE feed |
| `/api/public/{qrToken}/menu` | none | Customer menu for a table |
| `/api/public/{qrToken}/orders` | none | Place an order |
| `/api/public/orders/{publicToken}` | none | Read an order's status |

Use Swagger for the full request and response shapes.

### Try the main flow (about 2 minutes)

1. Log in at `/login` as `owner@pho.demo`.
2. Open `/admin/tables/print` and copy one table's link, or open `/admin/tables` and follow a QR code. A link looks like `http://localhost:8080/t/<token>`.
3. In a private window (so you're not logged in as staff), open that link, add items, and place the order.
4. Back in the staff window, open `/admin/kitchen`. The order appears without a refresh. Move it through Accepted, Cooking, Served and Paid.
5. Refresh the customer's order page to see the status change.

## 4. Database

PostgreSQL 16. Flyway applies the migrations in `src/main/resources/db/migration/` when the app starts, so the schema is always current after a deploy. Hibernate runs with `ddl-auto: validate`: it checks that the entities match the schema but never changes it. Any schema change therefore needs a new migration file, named `V7__description.sql`, and not a hand edit.

### Connection details

| | Local | Production |
|---|---|---|
| Host | `localhost` | `localhost` on the server. PostgreSQL listens there only, so it isn't reachable from outside |
| Port | `5432` | `5432` |
| Database | `tabletap` | `tabletap` |
| User | `tabletap` | `tabletap` |
| Password | `tabletap` | the `DB_PASSWORD` value in `/etc/tabletap/tabletap.env` |
| JDBC URL | `jdbc:postgresql://localhost:5432/tabletap` | `jdbc:postgresql://localhost:5432/tabletap` (`DB_URL` in the same file) |

### Open a psql shell

Local:

```bash
psql -h localhost -U tabletap -d tabletap
```

Production (run on the server):

```bash
sudo -u postgres psql -d tabletap
```

Useful commands inside psql: `\dt` (list tables), `\d staff_user` (describe a table), `\q` (quit).

### Connect a GUI client (DBeaver, TablePlus, pgAdmin)

Local only. Use host `localhost`, port `5432`, database `tabletap`, user `tabletap`, password `tabletap`.

For production, the database isn't published on the server, so a GUI client can't reach it directly. Run `psql` over SSH with the command above, open an SSH tunnel (`ssh -L 5433:localhost:5432 ubuntu@your-server`, then connect the client to `localhost:5433`), or copy a backup down (see below) and inspect it locally. Don't publish port 5432 to the internet.

### Useful queries

```sql
-- Restaurants and their staff
select t.name, s.email, s.role from tenant t join staff_user s on s.tenant_id = t.id;

-- Open orders, newest first
select table_label, status, total, created_at from orders
where status not in ('PAID', 'CANCELLED') order by created_at desc;
```

Check the actual column names with `\d orders` first. The status values come from `OrderStatus`.

### Back up and restore

Production backup, written to the server's current folder:

```bash
sudo -u postgres pg_dump tabletap > tabletap-$(date +%F).sql
```

Restore into an empty database. Restoring over existing data fails on duplicate keys, so wipe the database first or restore onto a fresh server. Stop the app while you restore:

```bash
sudo systemctl stop tabletap
sudo -u postgres psql -d tabletap < tabletap-2026-11-13.sql
sudo systemctl start tabletap
```

Copy the `.sql` file off the server regularly. The database lives on the server's disk, so it survives app and server restarts, but not a deleted server.

## 5. Tests

```bash
./gradlew test          # macOS / Linux
gradlew.bat test        # Windows
```

- Integration tests need a PostgreSQL database, named by `TEST_DATABASE_URL`. Use `tabletap_test` from [section 2](#2-run-locally), so tests never write into your development data:

  ```bash
  # macOS / Linux
  TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/tabletap_test ./gradlew test

  # Windows (PowerShell)
  $env:TEST_DATABASE_URL="jdbc:postgresql://localhost:5432/tabletap_test"; .\gradlew.bat test
  ```

- Without `TEST_DATABASE_URL` the database tests are skipped locally. CI sets it to its own PostgreSQL and fails rather than skips.
- The test database is never emptied. Every test creates its own uniquely named data. To start clean, drop and recreate `tabletap_test`.

Browser tests drive the real pages with Playwright and run as their own task:

```bash
./gradlew browserTest
```

Set `BROWSER_CHANNEL` to `chrome` or `msedge` to use a browser you already have. Without it, Playwright downloads its own Chromium on first run.

Full build the same way CI runs it, with both sets of tests: `./gradlew build`.

CI runs exactly this against the PostgreSQL that comes installed on its Ubuntu runner. The test plan and its progress are in [tests/TEST-PLAN.md](tests/TEST-PLAN.md).

## 6. Deploy to a server

The production setup is one small Linux server (for example AWS EC2 with Ubuntu 24.04). Everything is installed directly on it, with no Docker:

- **PostgreSQL 16**: the database. It listens on `localhost` only
- **The app**: the Spring Boot jar, run by systemd as the `tabletap` service on `localhost:8080`
- **Caddy**: the only public entry point. Gets an HTTPS certificate for your domain and forwards to the app

### Before you start

- A domain you control.
- Ports **22**, **80** and **443** open in the server's firewall or security group. Nothing else.
- A billing alarm on the cloud account.

### Steps

Run these on the server as a user with `sudo`.

1. **Add swap.** The Gradle build needs more memory than a 1 GB server has free.

   ```bash
   sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   ```

   To keep it after a reboot, also add `/swapfile none swap sw 0 0` to `/etc/fstab`.

2. **Install Java 21 and PostgreSQL 16.** Ubuntu 24.04 ships both:

   ```bash
   sudo apt update
   sudo apt install -y openjdk-21-jdk-headless postgresql
   ```

   The JDK is needed to build the jar on the server. Check with `java -version` and `sudo -u postgres psql -c 'select version()'`.

3. **Install Caddy.** Follow Caddy's official install instructions for Ubuntu (its apt repository). It starts as the `caddy` service.

4. **Point DNS at the server.** Create an `A` record for your domain pointing at the server's public IP. Caddy can only get a certificate after this resolves. Check with `nslookup your-domain`.

5. **Create the database.** Pick a long random password (`openssl rand -base64 24`):

   ```bash
   sudo -u postgres psql -v ON_ERROR_STOP=1 \
     -c "create role tabletap login password 'the-password'" \
     -c "create database tabletap owner tabletap"
   ```

6. **Clone and build.**

   ```bash
   git clone https://github.com/<your-user>/tabletap.git
   cd tabletap
   ./gradlew bootJar --no-daemon
   ```

   The first build takes several minutes on a small server.

7. **Install the app.** Create a system user that can't log in, then copy the jar into place:

   ```bash
   sudo useradd --system --no-create-home --shell /usr/sbin/nologin tabletap
   sudo install -D -m 644 build/libs/tabletap.jar /opt/tabletap/tabletap.jar
   ```

8. **Write the settings file.**

   ```bash
   sudo install -D -m 640 -g tabletap deploy/tabletap.env.example /etc/tabletap/tabletap.env
   sudo nano /etc/tabletap/tabletap.env
   ```

   | Variable | Value |
   |---|---|
   | `DB_URL` | leave as `jdbc:postgresql://localhost:5432/tabletap` |
   | `DB_USERNAME` | `tabletap` |
   | `DB_PASSWORD` | the password from step 5 |
   | `JWT_SECRET` | at least 32 characters. Generate one with `openssl rand -base64 48` |
   | `APP_BASE_URL` | `https://` plus your domain, e.g. `https://tabletap.example.com` |
   | `SPRING_PROFILES_ACTIVE` | `prod` for a real restaurant. See the warning below |
   | `SERVER_ADDRESS` | leave as `127.0.0.1`, so only Caddy can reach the app |

   > **Warning about `demo`.** `prod,demo` creates two demo restaurants with the public password `tabletap-demo`. Anyone on the internet can log in to them. Use `prod` alone unless the server is only a showcase. If you do use `demo`, know that the password is public.

   The file is readable by root and the `tabletap` group only. Never commit it.

9. **Start the app.**

   ```bash
   sudo cp deploy/tabletap.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now tabletap
   ```

   It starts again on its own after a crash or a reboot. `curl -s localhost:8080/actuator/health` should print `{"status":"UP"}` after about 20 seconds.

10. **Point Caddy at it.** Copy the Caddyfile, replace `tabletap.example.com` with your domain, and reload:

    ```bash
    sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
    sudo nano /etc/caddy/Caddyfile
    sudo systemctl reload caddy
    ```

11. **Check it.** Wait about a minute for Caddy to get the certificate, then:

    ```bash
    curl -s https://your-domain/actuator/health
    ```

    You should see `{"status":"UP"}`. Then open `https://your-domain/login` in a browser, and check the kitchen board updates live when you place an order from a table's link. That proves Caddy doesn't buffer the live stream.

12. **Create the first restaurant.** Go to `/register` and sign up. In production there is no default account.

### Changing settings later

Edit `/etc/tabletap/tabletap.env`, then `sudo systemctl restart tabletap`.

- `DB_PASSWORD` must match the database. To change it, first run `ALTER USER tabletap WITH PASSWORD 'new-value';` in psql (see [section 4](#4-database)), then update the file and restart.
- Changing `JWT_SECRET` logs everyone out. That's expected.
- Changing the domain needs DNS updated first, then the new domain in both `/etc/caddy/Caddyfile` (reload Caddy) and `APP_BASE_URL` (restart the app). Regenerate QR codes afterwards, since old ones carry the old address.

## 7. Operate the server

| Task | Command |
|---|---|
| Status of the three services | `systemctl status tabletap caddy postgresql` |
| Follow app logs | `journalctl -u tabletap -f` |
| Follow Caddy logs (HTTPS problems) | `journalctl -u caddy -f` |
| Restart the app | `sudo systemctl restart tabletap` |
| Deploy a new version (in the project folder) | `git pull && ./gradlew bootJar --no-daemon && sudo install -m 644 build/libs/tabletap.jar /opt/tabletap/tabletap.jar && sudo systemctl restart tabletap` |
| Memory use | `free -h`, and the `Memory:` line of `systemctl status tabletap` |
| Back up the database | see [Back up and restore](#back-up-and-restore) |

Deploying a new version runs any new Flyway migrations automatically on start. Take a backup first if the change includes a migration. The app is down for the few seconds it takes to restart.

To roll back, check out the previous commit, build and install the jar the same way. Migrations only go forward, so a rollback across a migration needs the backup.

## 8. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| App exits at start with "port 8080 was already in use" | Another program uses 8080. Stop it, or start with `--server.port=8081` |
| App exits at start with a database connection error | PostgreSQL isn't running, or the `tabletap` user or database is missing. See [Set up the database once](#set-up-the-database-once), or step 5 of the deploy |
| `tabletap` service fails with "Could not resolve placeholder" (JWT_SECRET, DB_URL, …) | `/etc/tabletap/tabletap.env` is missing, or the variable is blank. Check with `journalctl -u tabletap -n 50` |
| App won't start with a schema validation error | An entity no longer matches the schema. Add a new migration in `db/migration/`. Don't edit an old `V*` file that has already run |
| Staff login works on localhost but not on the server | Use the HTTPS domain, not the IP. Production cookies are `Secure`, so they are only sent over HTTPS |
| Phone can't load the QR page | Use the LAN IP as `APP_BASE_URL` (see [Scanning QR codes](#scanning-qr-codes-with-a-phone)). On the server, `APP_BASE_URL` must be the `https://` domain |
| Caddy says it can't get a certificate | DNS isn't pointing at the server yet, or ports 80/443 are blocked. Check `journalctl -u caddy` |
| Caddy returns 502 | The app isn't running or is still starting. Check `systemctl status tabletap` |
| Kitchen board stops updating | The live feed is an open connection. Reload the page. The app keeps events in memory, so it runs as one instance only |
| Gradle build on the server is killed or hangs | It ran out of memory. Add swap (step 1) |
| Tests skipped locally | Set `TEST_DATABASE_URL` (see [Tests](#5-tests)) |
