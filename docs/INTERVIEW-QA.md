# TableTap interview questions and answers

Answers are written from the code as it stands. File names in brackets show where to look so you can open the code and explain it yourself.

Four answers are marked **(yours)**. Only you know them, so fill them in before the interview: 2, 12, 133 and 168.

## Most likely to be asked

**1. Describe the project in one minute.**
TableTap is a QR menu and ordering app for restaurants. An owner registers, builds a menu with option groups such as size and toppings, and prints one QR code per table. A customer scans the code, picks dishes on their phone and orders with no account. The kitchen screen shows the order live, and staff move it through Placed, Accepted, Preparing, Served and Paid. It is Java 21 and Spring Boot 3.5 on PostgreSQL, with many restaurants sharing one database.

**2. Why did you build this, and what was the hardest part? (yours)**
Give your own reason. For the hardest part, good true candidates from the code are: tenant isolation with Hibernate `@TenantId` (the tenant must be set before the transaction opens), the race between Accept and Cancel, and sending kitchen events only after commit.

**3. How do you stop restaurant A from seeing restaurant B's data?**
Every tenant-owned table has a `tenant_id` column and every entity has a field marked `@TenantId`. Hibernate adds `tenant_id = ?` to every query and fills the column on insert, so I can't forget a `WHERE`. The value comes from `TenantContext`, which a filter fills from the JWT. With no tenant set, the resolver returns a zero UUID that matches no row, so the query returns nothing. [`TenantIdentifierResolver`, `TenantContextFilter`, `TenantIsolationTest`]

**4. How does a customer order without an account, and how does the server know the restaurant?**
The QR code holds `/t/{qrToken}`, a random UUID. `TableLookup` finds the table and its `tenant_id` by that token with plain SQL, then the controller runs the work inside `TenantContext.callAs(tenantId, ...)`. Placing an order returns a random `public_token`, which the status page uses to read the order later. [`CustomerController`, `TableLookup`, `OrderLookup`]

**5. Why does the server compute the price?**
A client can send anything. The request holds only item ids, option ids, quantities and notes. `OrderPricing` looks each one up in `CustomerMenu`, checks the option rules and adds the prices itself. There is no price field in `PlaceOrderRequest` to tamper with.

**6. How does the kitchen screen update without a refresh?**
Server-Sent Events. The page opens `EventSource("/api/admin/orders/stream")`, and the server keeps an `SseEmitter` per screen in a map keyed by tenant. When an order is placed or moved, `OrderService` publishes an `OrderChanged` event, and `OrderEvents` sends it to that tenant's emitters after the transaction commits. [`OrderEvents`, `kitchen.js`]

**7. Two staff press Accept and Cancel on the same order at the same time. What happens?**
`CustomerOrder` has a `@Version` column. Both requests read version 0. The first update sets version 1. The second runs `update ... where id = ? and version = 0`, matches no row, and Hibernate throws `ObjectOptimisticLockingFailureException`, which becomes HTTP 409. If the second request reads after the first commits, the state machine rejects it instead, also 409. `ConcurrencyTest` fires ten requests at once and checks exactly one wins.

**8. How does login work? Where is the JWT and why there?**
Login finds the user by email with plain SQL, checks the BCrypt hash and returns a JWT in a cookie named `tabletap_token`. The token is signed with HS256 and holds `sub` (user id), `tenantId`, `role`, and an 8-hour expiry. The cookie is `HttpOnly`, `Secure` and `SameSite=Strict`. JavaScript can't read an HttpOnly cookie, so an XSS bug can't steal the token the way it could from `localStorage`.

**9. What are the known limitations and how would you fix each?**
- Anyone with a table URL can order from anywhere. Fix: a short session code shown at the table, or staff opening the table. Today the Accepted step is the guard.
- SSE emitters live in memory, so one app instance only. Fix: Redis pub/sub or PostgreSQL `LISTEN/NOTIFY` between instances.
- No rate limiting. Fix: Bucket4j or a limit in Caddy on `/api/public/**` and `/api/auth/**`.
- Events are sent on the request thread. Fix: hand them to an executor.
- A token works until it expires even after logout. Fix: a denylist or a token version on the user.

**10. How did you test it, and what is not tested?**
Mostly full HTTP tests against a real PostgreSQL through `TestRestTemplate`, plus unit tests for pricing and the state machine, Playwright browser journeys, a concurrency test, a migration upgrade test and tests of the production profile. Not yet done: the items under P1 in `docs/tests/TEST-PLAN.md`, for example a token with a bad claim, and the isolation matrix over every admin route. The post-deploy check on a real server hasn't been done yet.

**11. How is it deployed?**
One small Ubuntu server with no Docker: PostgreSQL from apt listening on localhost only, the jar run by systemd on `127.0.0.1:8080`, and Caddy. Caddy is the only public entry, gets the HTTPS certificate and proxies to the app. Settings come from `/etc/tabletap/tabletap.env`. Deploying is `git pull`, `./gradlew bootJar`, copying the jar into place and `systemctl restart tabletap`. Say honestly whether you have deployed it yet: the README demo URL is still a placeholder.

**12. Which parts did you write yourself and which with AI help? (yours)**
Answer honestly. What matters is that you can open any file and explain why it is written that way, so practise doing that with the files named in this document.

## Multi-tenancy

**13. What models exist and why shared schema?**
Three: a database per tenant, a schema per tenant, or one schema with a `tenant_id` column. Separate databases or schemas isolate more strongly but every migration must run once per tenant and connections get harder to manage. For many small restaurants a shared schema is the cheapest to run and migrate, and `@TenantId` covers the main risk, a forgotten filter.

**14. What does `@TenantId` do?**
On select, Hibernate adds `tenant_id = ?` with the current tenant, including for `findById`. On insert it sets the column from the current tenant, so I never assign it by hand. The column is mapped `updatable = false`, so a row never changes tenant.

**15. What is `CurrentTenantIdentifierResolver` and when is it called?**
It is the hook Hibernate calls to ask "which tenant is this session for?". It is called when a session is opened, which with Spring is when a transaction starts. Mine returns `TenantContext.current()` or the zero UUID. It is registered through `HibernatePropertiesCustomizer`.

**16. Why a `ThreadLocal`? What breaks with other threads?**
A servlet request runs on one thread, so a `ThreadLocal` carries the tenant from the filter to Hibernate without passing it through every method. A new thread does not inherit it, so `@Async` work or a custom pool would run with no tenant and see nothing, which fails safe. To support that I would pass the tenant id explicitly and call `callAs` on the new thread.

**17. Why clear in `finally`?**
Tomcat reuses threads. If the value stayed, the next request on that thread, perhaps a public one with no login, would run as the previous restaurant. `finally` clears it even when the request throws.

**18. Why does `callAs` restore the previous tenant?**
So calls can nest. `DemoData` and tests call it, and a nested call that simply cleared would wipe the outer tenant. Restoring the previous value keeps each call self-contained.

**19. What is `NO_TENANT`?**
A UUID of all zeros. Hibernate requires a non-null tenant id, and no row has this one, so a query outside a tenant context matches nothing. `TenantIsolationTest.queriesWithoutATenantSeeNothing` proves it. Returning nothing is safer than returning everything.

**20. Why is `AccountCreator` separate from `AuthService`?**
Hibernate reads the tenant once, when the session is created, and `@Transactional` creates it on entry to the method. So the tenant must be set before the transactional method is entered. `AuthService.register` (not transactional) calls `callAs`, and inside it calls `AccountCreator.createTenantWithOwner` (transactional). If both were one method, the session would open with the zero tenant and the owner row would get the wrong `tenant_id`. A separate bean is also needed because `@Transactional` only works through the Spring proxy, not on a call within the same class.

**21. Why `JdbcTemplate` for login and token lookups?**
At that moment the tenant is unknown, and it is exactly what I'm trying to find. A tenant-filtered entity query would search under the zero tenant and find nothing. Plain SQL is not filtered, and each of these queries looks up one globally unique value: email, `qr_token` or `public_token`.

**22. Does `@TenantId` protect native SQL?**
No. It only covers queries Hibernate generates from entities. Native queries and `JdbcTemplate` calls are my responsibility, which is why there are only a few and each one filters on a unique token.

**23. Why is the tenant filter after `BearerTokenAuthenticationFilter`?**
The tenant filter reads the `JwtAuthenticationToken` from the `SecurityContext`. That only exists after the bearer filter has validated the token.

**24. Why is `tenant` not tenant-scoped?**
It is the root. Its `id` is the tenant id, and it is read by lookups such as `TableLookup` before any tenant is known.

**25. Tenant A calls an endpoint with tenant B's id. 403 or 404?**
404. The filtered query finds no row, so the service throws not found. That also avoids telling A that the id exists. `TenantIsolationTest.anotherTenantsStaffUserCannotBeFetchedById` checks this.

**26. How would you add database-level protection?**
PostgreSQL row-level security: enable it on each table with a policy like `tenant_id = current_setting('app.tenant_id')::uuid`, and run `set local app.tenant_id = ...` at the start of each transaction. Then even native SQL is filtered. The app would need to connect as a role that is not the table owner.

**27. Why does every tenant index start with `tenant_id`?**
Every query has `tenant_id = ?`, so that must be the leading column for the index to be used. For example `orders (tenant_id, created_at)` serves the kitchen list directly.

**28. One restaurant grows very large. What happens?**
It shares CPU, connections and indexes with everyone, the noisy neighbour problem. First steps are a per-tenant rate limit and checking slow queries. If it kept growing I would move that tenant to its own database, which a shared schema makes possible because every row already carries its tenant id.

## Authentication and security

**29. What is in the JWT?**
`sub` is the user id, `iat` and `exp` give an 8-hour life, `tenantId` and `role` are custom claims. It is signed with HS256. It is signed, not encrypted, so nothing secret goes in it. [`TokenService`]

**30. HS256 versus RS256?**
HS256 uses one shared secret to sign and verify. RS256 signs with a private key and verifies with a public key. One app both issues and checks tokens here, so a shared secret is enough. I would switch to RS256 if other services had to verify tokens without being able to create them.

**31. Why a cookie and not `localStorage` with a header?**
Any script on the page can read `localStorage`, so one XSS bug leaks the token. An HttpOnly cookie is invisible to scripts. The cost is CSRF, which `SameSite=Strict` handles.

**32. What do the three cookie flags do?**
`HttpOnly`: JavaScript can't read it, which limits XSS damage. `Secure`: sent only over HTTPS, so it can't be sniffed. `SameSite=Strict`: the browser does not attach it to requests that start from another site, which stops CSRF.

**33. You disabled CSRF for `/api/**`. Why is that safe?**
CSRF works because the browser attaches the cookie to a request forged from another site. With `SameSite=Strict` it doesn't. It would stop being safe if the cookie were changed to `Lax` or `None`, if an attacker controlled a sibling subdomain (same-site is wider than same-origin), or for very old browsers that ignore `SameSite`.

**34. Why the OAuth2 resource server starter with no OAuth server?**
It gives a tested JWT encoder, decoder and authentication filter (Nimbus), so I didn't write my own token parsing. I only supply the secret key, a resolver that reads the token from the cookie, and a converter for the role claim.

**35. Why read the cookie only on admin paths?**
If a token is presented, Spring validates it, and an invalid one gets 401 even on a public path. An expired cookie would then lock the user out of `/login`. So the resolver returns null on public paths and the cookie is ignored there.

**36. What happens with an expired cookie on `/login`?**
Nothing bad: the page loads, the user logs in, and the new cookie replaces the old one. `AuthFlowTest.staleCookieDoesNotBlockLoggingInAgain` covers it.

**37. Why redirect for pages and 401 for the API?**
A person opening `/admin/menu` should land on the login page. JavaScript calling the API needs a status code it can react to. `api.js` sends the browser to `/login` when it sees 401. [`SecurityConfig.entryPoint`]

**38. After logout, is the token still valid?**
Yes, until it expires. Logout only tells the browser to delete the cookie. That is the trade-off of stateless tokens. To revoke, I would store a token id (`jti`) denylist, or a `token_version` on the user that is checked on each request, or use short access tokens with a refresh token.

**39. Why no refresh token? What happens after 8 hours?**
Eight hours covers a shift and keeps the design simple. When it expires, the kitchen stream fails, `kitchen.js` calls `/api/admin/me`, gets 401 and the page goes to login. A refresh token would be the next step for a real product.

**40. Why BCrypt?**
It is deliberately slow and has a work factor, so guessing passwords from a stolen table is expensive. Each hash has its own random salt stored inside it, so equal passwords give different hashes and precomputed tables are useless. `BCryptPasswordEncoder` uses strength 10 by default.

**41. What is `dummyHash` for?**
If the email doesn't exist and I return at once, the response is much faster than a real BCrypt check, and an attacker can tell which emails are registered by timing. So login always runs one BCrypt comparison, against a dummy hash when the user is missing.

**42. What is the 72-byte limit?**
BCrypt only uses the first 72 bytes of a password. Two long passwords with the same first 72 bytes would both match. Registration rejects longer passwords, counted in UTF-8 bytes since non-ASCII characters take more than one, and login refuses them without hashing. [`AuthDtos.fitsBcrypt`]

**43. Why the same message for wrong email and wrong password?**
Different messages would let someone test which emails have accounts.

**44. What does `JwtProperties` check at startup?**
The secret must be at least 32 bytes, which HS256 needs, and must not start with `change-me`, the public example value in `deploy/tabletap.env.example`. Failing at startup means a misconfigured server never accepts a login, instead of running with a secret anyone can read on GitHub. A test found that the example value passed the length check, so I added the second check.

**45. What if a signed token has a bad `tenantId` claim?**
Only someone with the secret could create one. Today `UUID.fromString` would throw and the response would be 500. It should be 401. This is an open item in my test plan, and the fix is to validate the claims in the decoder or catch the error in the filter.

**46. Where is OWNER versus STAFF enforced?**
It isn't yet. Registration creates an OWNER, the role goes into the token and becomes a `ROLE_OWNER` authority, but no rule checks it and there is no endpoint to add STAFF users. The model is ready for it. I would add `hasRole("OWNER")` on menu and table management and leave the kitchen endpoints to both roles.

**47. Why `anyRequest().denyAll()`?**
Deny by default. A new endpoint that I forget to classify is blocked instead of being open.

**48. Why permit `DispatcherType.ASYNC`?**
When an SSE stream ends, the container dispatches the request again as ASYNC. It was already authorized when it started, and the app is stateless, so the second pass would be rejected and log an error.

**49. How do you prevent XSS?**
All JavaScript builds DOM through the `el` helper in `api.js`, which adds text as text nodes and never uses `innerHTML`. Thymeleaf escapes with `th:text`. A Playwright test puts `<img onerror>` in a dish name and a note and checks it shows as text on the customer, order and kitchen pages.

**50. How do you prevent SQL injection?**
Every `JdbcTemplate` query uses `?` parameters, and JPA queries are parameterized. Tokens are also parsed with `UUID.fromString` first, so anything that isn't a UUID never reaches the database.

**51. Anyone with a table URL can order from home. Fix?**
Options: a short code that staff give or that rotates on a table display, a table session staff open and close, or a location check. For now staff must Accept an order before cooking, and the owner can regenerate a QR code to kill a leaked URL.

**52. No rate limiting. What does that allow?**
Password guessing on login, flooding a kitchen with fake orders, and mass registration. I would add a per-IP limit at Caddy or with Bucket4j in a filter, stricter on login and on order placement per QR token.

**53. Can a token be guessed?**
No. They are version 4 UUIDs from `UUID.randomUUID()`, which uses a secure random generator and has 122 random bits. They are capabilities though: whoever has the URL has access, which is why regeneration exists.

**54. The public order response includes the internal `id`. Problem?**
Not exploitable, since admin endpoints need a login for the right tenant and the id is a random UUID. But a customer has no use for it, so the clean fix is a separate public response record without it.

**55. Do you need CORS?**
No. The pages and API are on the same origin, so the browser's same-origin policy is satisfied. CORS is only for letting another origin call the API.

## Orders, pricing and the state machine

**56. Walk through placing an order.**
`CustomerController.place` validates the body, resolves the QR token to a table and tenant, and calls `OrderService.place` inside `callAs`. The service builds the `CustomerMenu`, `OrderPricing.price` validates and prices each line, a `CustomerOrder` with its items and options is saved, and an `OrderChanged` event is published. After commit the kitchen gets the event, and the response is 201 with the `publicToken`.

**57. What does `OrderPricing` reject?**
An item that is not on the customer menu, quantity below 1 or above 99, the same option twice, fewer options than `minSelect` or more than `maxSelect` in a group, and an option that isn't one of this item's available options. Each throws `InvalidOrderException` with a message for the customer.

**58. Why static methods and no bean?**
It is a pure function: menu and lines in, priced order out, no database and no state. That makes it trivial to unit test without Spring. [`OrderPricingTest`]

**59. Why `long` for money?**
`double` can't represent decimal amounts exactly, so sums drift. A whole number of the smallest unit (đồng, cents) is exact and fast. `BigDecimal` would also be correct but adds scale and rounding rules I don't need. The front end divides by the currency's fraction digits only to display. [`money.js`]

**60. Why `Math.addExact` and `multiplyExact`?**
Normal `long` arithmetic wraps silently on overflow, which could turn a huge total into a small or negative one. The exact methods throw instead.

**61. Why validate against `CustomerMenu`?**
It is the same filtered view the customer was shown: only available items and options, and no item whose required group has too few options left. Pricing against it means a customer can only order what the menu offered, and the rules live in one place.

**62. An option id belongs to another item. How is it caught?**
Pricing walks this item's groups and collects the chosen ids it finds. If the number found is smaller than the number chosen, some id didn't belong, and the order is rejected.

**63. Why 422, 409 and 400?**
400 is a malformed or invalid body, from bean validation. 422 is a well-formed order that breaks a menu rule, such as a sold-out item. 409 is a conflict with current state: an illegal status move, a lost race or a duplicate.

**64. Why copy names and prices into the order?**
An order records what was sold at that moment. If it joined to `menu_item`, raising a price or renaming a dish would rewrite past orders. Copying is intentional history, not a normalization mistake.

**65. Why nullable foreign keys with `on delete set null`?**
So an owner can delete a table or a dish without deleting or blocking on past orders. The order keeps `table_label` and `item_name` as text.

**66. Why an enum for the state machine?**
There are six states and a fixed set of moves. A `switch` in `OrderStatus.next()` is readable, compile-checked (a new state forces me to handle it) and easy to test exhaustively. A library or a table would be more machinery than the problem needs.

**67. Why can't PREPARING be cancelled?**
Food is already being cooked, so cancelling is no longer a simple status change. It would need a void or refund process, which is out of scope. I would add it as an explicit new transition with its own rules.

**68. Why is the rule in `CustomerOrder.moveTo`?**
The entity protects its own state. No caller can set an illegal status, because there is no setter, only `moveTo`.

**69. The price changes while an item is in the cart. What does the customer pay?**
The current price. The cart stores no prices, only ids, and the server prices at the moment of ordering. If the item was edited with `PUT`, its option ids changed, so an order using old option ids gets 422, and the page reloads the menu and drops the stale lines.

**70. Double tap on the order button?**
The button is disabled while the request is in flight, which covers the normal case. The server has no idempotency check, so a retry after a lost response could create two orders. The fix is an idempotency key generated by the client and stored with a unique constraint.

**71. Why the newest 200, oldest first?**
The kitchen works oldest first, so the list is displayed that way. But the limit takes the newest, because orders that were served and never marked paid pile up, and taking the oldest 200 would hide new orders behind them.

**72. Why `CustomerOrder` and table `orders`?**
`Order` is a reserved word in JPQL and `order` is reserved in SQL. The same reason gives `menu_option` instead of `option`.

**73. Why `List.copyOf(items)`?**
Callers get a read-only copy, so the only way to change an order's items is through the entity itself.

## Concurrency and transactions

**74. What is optimistic locking?**
Assume conflicts are rare, take no lock, and detect a conflict when writing. Hibernate adds `and version = ?` to the update and increments the version. Zero rows updated means someone else changed the row first.

**75. Why optimistic and not pessimistic?**
Pessimistic locking (`select ... for update`) holds a row lock and makes others wait. Two staff touching the same order in the same instant is rare, the transaction is short, and the right answer for the loser is "reload", not "wait". Optimistic gives that with no locks held.

**76. What does the client see on a conflict?**
HTTP 409 with "Someone else changed this at the same time. Reload and try again." The kitchen page shows the message and reloads the list. [`ApiExceptionHandler`, `kitchen.js` `move`]

**77. Without `@Version`, what goes wrong?**
Both requests read PLACED, both pass the state check, both update. The last write wins silently, and both screens get a success. One staff member believes the order is accepted while the database says cancelled.

**78. How do two same-named restaurants get unique slugs?**
`AccountCreator` checks `existsBySlug` and adds `-2`, `-3` and so on. Two requests can both pick the same free slug. The unique constraint on `slug` rejects the second, `AuthService.register` catches the violation and retries, and the retry now sees the taken slug.

**79. Why three attempts and `saveAndFlush`?**
Three bounds the retry so a real error can't loop forever. It is a limit, not a guarantee: the concurrency test with ten at once passes, but very heavy contention could in theory exhaust it. `saveAndFlush` sends the insert at that line, so the tenant row exists before the owner row and the violation surfaces inside the method.

**80. Why a service check and a unique constraint?**
The check gives a clear message in the normal case. But check-then-insert is not atomic, so two requests can both pass the check. The constraint is the real guarantee.

**81. Why map only `23505` and `23503` to 409?**
Those SQL states are unique and foreign key violations, which is what losing a race looks like, for example deleting a category while an item is added to it. Any other integrity error is a bug in my code, so it is rethrown and shows as 500 rather than being hidden. A concurrency test found the foreign key case returning 500, and I fixed it.

**82. An order is placed while its item is edited. Outcomes?**
Either 201 priced entirely from the old menu, or 422. Never a mix. The edit replaces the option groups, so their ids change. If the order transaction sees the new groups, the old option id isn't found and the order is rejected.

**83. PostgreSQL's default isolation level?**
Read Committed. Each statement sees data committed before it started. It prevents dirty reads but allows non-repeatable reads, phantoms and lost updates, which is why the order needs `@Version`.

**84. How does `@Transactional` work?**
Spring wraps the bean in a proxy. A call through the proxy opens a transaction, runs the method, then commits, or rolls back on a runtime exception. A call from one method to another in the same class skips the proxy, so the annotation on the second method does nothing. That is one reason `AccountCreator` is its own bean.

**85. What does `readOnly = true` do?**
It tells Hibernate not to flush or track changes, which saves work, and it documents that the method only reads.

**86. `updateCategory` never calls `save`. Why is it saved?**
Dirty checking. An entity loaded inside a transaction is managed. At commit Hibernate compares it with the loaded state and writes an update for what changed.

**87. Why `@Transactional` on services?**
A service method is one business operation, so that is the right unit to commit or roll back. Controllers deal with HTTP. With `open-in-view: false` the connection is also released as soon as the service returns.

**88. How did you test real concurrency?**
`ConcurrencyTest` starts ten threads that each wait at a `CyclicBarrier`. When all ten have arrived the barrier releases them together, and each sends a real HTTP request. The assertions check the set of status codes, for example exactly one 200 and the rest 409.

## Live updates (SSE)

**89. SSE, WebSocket or polling?**
The kitchen only needs server-to-browser messages, which is what SSE does over plain HTTP, with automatic reconnect built into `EventSource`. WebSocket is two-way and more complex. Customers poll every 5 seconds because there can be many diners and a status change is not urgent, so I avoid one open connection per phone.

**90. How does `SseEmitter` work? Which thread sends?**
The controller returns the emitter, the servlet request goes async and the response stays open. Any thread can then call `emitter.send`. Order events are sent by the thread that just committed the transaction, and heartbeats by Spring's scheduler thread.

**91. Why `@TransactionalEventListener`?**
Its default phase is after commit. A direct call or a plain `@EventListener` would run inside the transaction, before it is known to succeed.

**92. What if events were sent before commit?**
The kitchen could start cooking an order that then rolls back and doesn't exist. Or the screen could reload the list before the commit and not find the order it was just told about.

**93. Why `ConcurrentHashMap` and `newKeySet()`?**
Request threads and the scheduler thread add, remove and iterate at the same time. A `HashMap` or `HashSet` could lose entries or throw `ConcurrentModificationException`. `computeIfAbsent` also creates the per-tenant set atomically.

**94. Why a heartbeat every 25 seconds?**
Proxies and load balancers close connections that look idle. The comment line keeps the stream alive, and a failed send is how the server learns a client has gone.

**95. How do you detect a tablet that lost Wi-Fi?**
The server can't know until it writes. The next heartbeat or event fails with an `IOException`, and `send` removes that emitter.

**96. What happens at the 30-minute timeout?**
The emitter completes and is removed from the set. The browser's `EventSource` reconnects by itself, and the `open` handler reloads the full list.

**97. A screen was offline for two minutes. How does it catch up?**
Missed events are not replayed. On every (re)connection `kitchen.js` calls `reload()`, which fetches all open orders from the API, so the board is correct again.

**98. A slow kitchen connection can delay the customer. Why, and the fix?**
`emitter.send` writes to the socket on the thread that handled the order request. If one screen's connection is stalled, that write can block. The fix is to send on a separate executor, ideally with a small queue per emitter and a write timeout. It is listed as a known limitation.

**99. Why a single instance? How would you run two?**
Emitters are in one JVM's memory, so an order handled by instance A never reaches a screen connected to B. With several instances, each would publish order changes to Redis pub/sub or PostgreSQL `NOTIFY`, and each instance would forward what it hears to its own emitters.

**100. How does a restaurant get only its own events?**
`subscribe` files the emitter under the tenant id from the JWT, and `OrderChanged` carries the tenant id of the order. `publish` only looks up that tenant's set.

**101. How many open connections can the server hold?**
An async SSE connection does not hold a Tomcat worker thread, so the limit is connections and memory, not the 200-thread pool. Tomcat's default connection limit is 8192. On this server the 384 MB heap is the practical limit. I have not load tested it.

## Database and JPA

**102. Why Flyway with `validate`?**
Schema changes are versioned SQL files that are reviewed, repeatable and identical in every environment. `ddl-auto: update` guesses changes, can't rename or drop safely, and gives no history. `validate` makes the app refuse to start if an entity doesn't match the schema.

**103. Why never edit an applied migration?**
Flyway stores a checksum of each applied file. If the file changes, startup fails with a checksum mismatch, and databases that already ran the old version would differ from fresh ones. A change is always a new file.

**104. How do you add a `NOT NULL` column to a table with data?**
Give it a default, as V6 does: `add column version bigint not null default 0`. PostgreSQL 11 and later stores a constant default without rewriting the table. Without a sensible default: add it nullable, backfill, then set `not null`. `MigrationUpgradeTest` migrates to V5, inserts an order, migrates to V6 and reads it back.

**105. Why UUID primary keys?**
They can be generated in the application before the insert, can't be guessed or counted, and never collide across tenants. The cost is 16 bytes instead of 8 and random insert positions in the index, which is fine at this size.

**106. Why `open-in-view: false`?**
With it on, the Hibernate session and a database connection stay open until the response is written, and lazy loading can silently run queries from the controller. Off, everything must be loaded inside the service, where I convert to records. Touching an unloaded lazy collection after the transaction throws `LazyInitializationException`.

**107. What is N+1, and what does `default_batch_fetch_size` do?**
Loading 50 items and then each item's option groups one by one is 1 + 50 queries. With batch fetching, when one lazy collection is needed Hibernate loads it for up to 50 parents in one `where menu_item_id in (...)` query. `JOIN FETCH` or an entity graph would load it in the first query, but fetching two nested collections that way multiplies rows.

**108. What do `cascade = ALL` and `orphanRemoval` do?**
Saving the order saves its items and their options with it, since they are created together and have no life of their own. `orphanRemoval` deletes a child that is removed from the collection.

**109. Why flush between clearing and re-adding option groups?**
Within one flush Hibernate runs inserts before deletes. The explicit flush sends the deletes of the old groups first, so the old and new rows never exist together.

**110. Replacing option groups changes ids. What breaks?**
A cart holding old option ids. The order is rejected with 422, then `customer.js` reloads the menu, removes the stale lines and tells the customer. The simple replace was chosen over diffing groups, and this is its cost.

**111. Why `timestamptz` and `Instant`?**
Both mean a point in time in UTC with no zone ambiguity. The browser converts to local time for display.

**112. Why check constraints when Java validates?**
The database is the last line of defence and outlives the code. A bug, a manual SQL fix or a future second app can't store a negative price or an unknown status.

**113. Why `EnumType.STRING`?**
`ORDINAL` stores the position, so reordering or inserting an enum value would silently change the meaning of existing rows. Strings are stable and readable in SQL.

**114. Which indexes exist?**
Each tenant table has one starting with `tenant_id`, for example `orders (tenant_id, created_at)` for the kitchen list and `menu_item (tenant_id, category_id, sort_order)` for the menu. Unique constraints give indexes on `qr_token`, `public_token`, slug, email and `(tenant_id, label)`, which serve the token lookups. I would confirm with `EXPLAIN ANALYZE` that a query shows an index scan.

**115. How does connection pooling work here?**
Spring Boot uses HikariCP, default maximum 10 connections. A request borrows one for the length of its transaction and returns it. Open SSE streams hold no database connection.

**116. Backup and restore?**
`sudo -u postgres pg_dump` to a dated SQL file, copied off the server, and restore with `psql` into an empty database. The commands are in `docs/GUIDE.md`. A restore drill is still on my list.

## API and code design

**117. Why package by feature?**
Everything about orders is in `order`, everything about tables in `floor`. A change touches one package, and classes can be package-private (`AccountCreator` is). Packaging by layer spreads one feature over `controller`, `service` and `repository`.

**118. Why records for DTOs, and why keep entities inside services?**
Records are immutable and short. Keeping entities inside means the API shape is chosen on purpose, fields like `passwordHash` can't leak, lazy loading can't fire during JSON serialization, and the schema can change without changing the API.

**119. Why constructor injection?**
Dependencies are `final` and visible, the object can't exist half-built, and a test can create it with `new`. Field injection hides dependencies and needs reflection.

**120. What is `ProblemDetail`?**
The standard error format from RFC 7807, JSON with `type`, `title`, `status`, `detail` and `instance`. For example `{"title":"Conflict","status":409,"detail":"A table with this label already exists"}`. The front end shows `detail` to the user.

**121. How does `@RestControllerAdvice` work?**
It is a global exception handler. When a controller throws, Spring finds the matching `@ExceptionHandler` method and uses its return value as the response body.

**122. How does bean validation work?**
Annotations such as `@NotBlank` and `@Size` sit on the record components, and `@Valid` on the controller parameter triggers the check before the method runs. A failure is 400 with a problem detail body. Cross-field rules use `@AssertTrue`, as with the password byte limit.

**123. `PUT` versus `PATCH`, and why a separate availability `PUT`?**
`PUT` replaces the whole resource and is idempotent, `PATCH` changes part. Marking a dish sold out is the most frequent action in a shift, and sending the full item would also replace its option groups and change their ids. A small `PUT /items/{id}/availability` avoids that.

**124. Is `POST /orders/{id}/transition` RESTful?**
It is an action endpoint, which is a common, pragmatic choice when a change must follow rules. The alternative is `PATCH /orders/{id}` with `{"status": ...}`, which hides the fact that only some moves are allowed. Each transition can only succeed once, so a repeat gets 409.

**125. Why Thymeleaf and plain JavaScript?**
The project is about the backend. Thymeleaf serves page shells, and small scripts call the same JSON API a separate front end would. No build step and no second toolchain.

**126. Why `localStorage` for the cart?**
Customers have no account, so there is nothing on the server to attach a cart to. The cart is keyed by QR token and survives a reload. If storage is blocked, as in some private modes, the `storage` wrapper falls back to memory and the page still works but forgets the cart on reload. Two tabs can overwrite each other's cart, which I accept.

**127. How is the QR image made, and why no PDF?**
ZXing's `QRCodeWriter` renders a 600-pixel PNG with medium error correction, so a scratched sticker still scans. The print sheet is an HTML page printed from the browser, because the built-in PDF fonts can't render Vietnamese names.

**128. What is `APP_BASE_URL`?**
The public address written into each QR code. If it is wrong, for example `localhost`, a phone that scans the code can't reach the server. In production it is set in `/etc/tabletap/tabletap.env` to the `https://` domain.

**129. How does springdoc build Swagger?**
It scans the controllers and their records at startup, generates an OpenAPI document at `/v3/api-docs`, and serves Swagger UI on top of it. It is limited to `/api/**`.

**130. How does `DemoData` avoid duplicates?**
It is a bean only under the `demo` profile. For each demo restaurant it first counts staff users with the owner's email and skips seeding if one exists. `ProdRestartTest` checks a restart creates nothing new.

## Testing

**131. One example of each test level.**
Unit: `OrderPricingTest` and `OrderStatusTest`, no Spring. Integration: `OrderFlowTest` and `KitchenApiTest`, the full app on a real database over HTTP. End to end: `BrowserJourneyTest` with Playwright driving a real browser.

**132. Why real PostgreSQL and not H2?**
The app relies on PostgreSQL behaviour: `timestamptz`, check constraints, SQL states `23505` and `23503`, and the Flyway scripts themselves. H2 would test a different database.

**133. You removed Testcontainers. Why? (yours)**
The project doesn't use Docker at all, and Testcontainers needs it. Tests use the database named by `TEST_DATABASE_URL` instead. The trade-off: no Docker needed and faster startup, but the database isn't thrown away, so tests must create unique data.

**134. Why skip locally but fail in CI?**
Locally a missing database shouldn't block someone from running unit tests. In CI a skipped test looks green while proving nothing, so `DatabaseAvailableCondition` throws when `CI` is set and there is no database.

**135. How do tests avoid interfering?**
Each test registers its own restaurant with a random email, so it works in its own tenant. Tenant isolation, the feature under test, also keeps tests apart.

**136. Why HTTP tests rather than `MockMvc` or mocks?**
They go through the real filter chain, cookie handling, JSON, transactions and database, which is where the bugs in this app would be. Mocks would mostly test my assumptions. The cost is speed.

**137. How does `TenantIsolationTest` prove isolation?**
It registers two restaurants and checks: A's staff list contains only A, A gets 404 for B's user id while B gets 200, repository calls under A's tenant see only A, a query with no tenant sees nothing, and the context is empty after `callAs`.

**138. What do the production profile tests check?**
Under `prod`: the cookie has `Secure`, a forwarded HTTPS request redirects to an HTTPS login URL, only `/actuator/health` is reachable, and the app refuses to start with a missing, short or placeholder `JWT_SECRET`. All other tests run on `dev`, so without these the deployed settings would be untested.

**139. What does `MigrationUpgradeTest` prove?**
Other tests migrate an empty database. A deploy migrates one that has data. This test builds a schema at V5, inserts an order, migrates to latest and checks the order survived with `version = 0`.

**140. Why Playwright for Java, and why a separate task?**
It keeps one toolchain, runs under Gradle and uses the same database. Browser tests are slow, so they are tagged `browser`, excluded from `test`, and run by `browserTest`, which `check` depends on. So `./gradlew test` is fast and `./gradlew build` runs everything.

**141. Can the concurrency test be flaky?**
A race might not happen on a given run, so the assertions accept every legal outcome and reject illegal ones such as two winners or a 500. The barrier makes real overlap likely, and the two-way races repeat 15 rounds.

**142. What is your coverage?**
I don't measure it yet. Adding JaCoCo is on the plan, to find untested branches, not as a pass threshold, since a percentage gate invites tests written to satisfy the number. Run `./gradlew test` before the interview so you can quote the current test count.

**143. Which bugs did tests find?**
Two. The example `JWT_SECRET` from the example settings file was long enough to pass the length check, so a server could have run with a public secret. And deleting a category while an item was added to it returned 500 instead of 409. I had also predicted ten parallel same-name registrations would fail, and the test showed that prediction was wrong.

**144. What is still untested?**
The P1 list in `docs/tests/TEST-PLAN.md`: tokens with bad claims, token after logout, the isolation matrix over every admin route, body size limits, the 200-order list limit, emitter cleanup, and the post-deploy check on a real server.

## Build and deployment

**145. Why no Docker?**
The project doesn't use Docker anywhere. One app, one database and one proxy on one server don't need containers: systemd already restarts the app, apt installs PostgreSQL and Caddy, and the deploy unit is a single jar. It also means nothing to install on the Windows development machine beyond Java and PostgreSQL, and CI uses the PostgreSQL that comes on the GitHub runner. The cost: the server is set up by hand following `docs/GUIDE.md`, and dev and prod versions of PostgreSQL and Java have to be kept in step by me rather than pinned in an image.

**146. Why a dedicated system user?**
The app runs as `tabletap`, a system user with no shell and no home. If the app is compromised, the attacker has an unprivileged user instead of root. The unit also sets `ProtectSystem=strict`, `ProtectHome` and `PrivateTmp`, so the process can't write to the system or read users' files.

**147. What does `-Xmx384m` do?**
It caps the Java heap at 384 MB. The JVM needs roughly another 150 MB for metaspace, thread stacks and native memory. A fixed cap keeps the app's memory predictable next to PostgreSQL and Caddy on a small server, instead of the JVM sizing its heap from the whole machine's RAM.

**148. What does Caddy do?**
It is the reverse proxy and the only process listening on public ports. It obtains and renews a Let's Encrypt certificate for the domain automatically, redirects HTTP to HTTPS, compresses responses and forwards to `localhost:8080`. It flushes `text/event-stream` immediately, so SSE needs no extra settings.

**149. Why `forward-headers-strategy: native`?**
The app receives plain HTTP from Caddy. This setting makes Tomcat trust `X-Forwarded-Proto` and `X-Forwarded-Host` from local proxies such as `127.0.0.1`, so redirects to the login page use `https` and the right host. Without it the redirect would point at `http`.

**150. Why is PostgreSQL not exposed?**
Ubuntu's PostgreSQL listens on `localhost` only by default, and the app listens on `127.0.0.1` (`SERVER_ADDRESS`). Only ports 22, 80 and 443 are open in the security group, so the database and the app's port 8080 can't be reached from the internet.

**151. What does the systemd unit do?**
`deploy/tabletap.service` starts the jar after PostgreSQL (`After=` and `Requires=postgresql.service`), restarts it 5 seconds after a crash, starts it on boot, sends its output to the journal, and reads settings from `/etc/tabletap/tabletap.env`. `SuccessExitStatus=143` makes a normal stop by SIGTERM count as clean.

**152. How are secrets handled?**
They are environment variables in `/etc/tabletap/tabletap.env`, owned by root and readable only by the `tabletap` group (mode 640). The repository has only `deploy/tabletap.env.example` with placeholder values. The app refuses to start with a missing, short or placeholder `JWT_SECRET`.

**153. What does CI do?**
On every push to `main` and every pull request, it starts the PostgreSQL 16 that comes installed on the Ubuntu 24.04 runner, creates the test user and database, sets up Java 21 and runs `./gradlew build`, which includes unit, integration and browser tests. It uploads test reports on failure.

**154. How do you deploy a new version?**
Back up if there is a migration, then `git pull`, `./gradlew bootJar`, copy the jar to `/opt/tabletap/` and `sudo systemctl restart tabletap`. There are a few seconds of downtime while the app restarts, and Flyway runs new migrations on start. Rollback is checking out the previous commit and doing the same. Migrations only go forward, so a schema change should be backward compatible or I restore the backup.

**155. How is 1 GB of RAM divided?**
Roughly: the app's 384 MB heap plus about 150 MB of JVM overhead, PostgreSQL with default settings at around 100 MB, Caddy at a few tens of MB, and the rest for the OS. Swap is for the Gradle build on the server, which needs more than what is free. I have not measured this under load.

**156. Why only `/actuator/health`?**
Other actuator endpoints expose environment values, bean lists and heap dumps. Health is all a monitor needs, and it shows no details.

**157. How would you find why an order failed in production?**
`journalctl -u tabletap` for the stack trace, and the database for the order row. That is basic. For a real service I would add a request id in the logs and metrics and alerts.

**158. What differs between profiles?**
`dev` (the default): local database, a fixed secret, cookie not `Secure` because it runs on plain HTTP. `prod`: everything from environment variables, forwarded headers trusted, `Secure` cookie. `demo`: adds the two seeded restaurants and combines with either.

## Scaling and "what if"

**159. 1,000 restaurants at dinner time. What breaks first?**
Probably CPU on login because of BCrypt, then the 10-connection pool, then the request-thread SSE sends. The public menu is rebuilt from the database on every request and would be the first thing I cache. I would measure before changing anything.

**160. How would you scale to several instances?**
The app is already stateless for auth, since the JWT is self-contained. The only shared state is the emitter map, so I would add Redis pub/sub or `LISTEN/NOTIFY` for events and put a load balancer in front. The scheduled heartbeat can run on every instance.

**161. How would you add online payment?**
Create a payment with a provider, redirect the customer, and trust only the provider's signed webhook to mark it paid, never the browser. I would track payment status separately from kitchen status, handle duplicate webhooks with an idempotency key, and add refund handling for cancellations.

**162. Multiple branches per restaurant?**
Add a `branch` table under tenant, and a `branch_id` on tables, orders and staff. The tenant stays the security boundary, and branch becomes an ordinary filter. Menus could be shared at tenant level with per-branch availability.

**163. Inventory so the last portion isn't sold twice?**
A stock column decremented with one atomic statement: `update ... set stock = stock - ? where id = ? and stock >= ?`, and reject the order if no row was updated. This runs in the same transaction as the order.

**164. Caching the public menu?**
Cache `CustomerMenu` per tenant with Caffeine and evict that tenant's entry on any menu write. With several instances the eviction must be broadcast, or I use a short TTL. Pricing must read from the same source so a customer can't order at a stale price.

**165. Staff with fewer permissions?**
An owner-only endpoint to create STAFF users, then `hasRole("OWNER")` on menu, tables and staff management. The role is already in the token and mapped to an authority.

**166. Analytics without slowing ordering?**
Orders already snapshot names and prices, so reports are plain aggregates on `orders` and `order_item`. I would add an index for the date range, and if it got heavy, a nightly summary table or a read replica.

**167. Would you split into microservices?**
No. One small team, one database and features that share transactions (an order needs the menu and the table). A modular monolith packaged by feature is simpler to deploy and debug. The first candidate to extract would be the live event delivery.

**168. What would you change if you started again? (yours)**
Give your own view. Honest options from the code: send SSE off the request thread from the start, enforce roles, add rate limiting and an idempotency key on order placement, and keep option ids stable on item updates.

## Java and Spring fundamentals

**169. Which Java 21 features do you use?**
Records for DTOs and value types, switch expressions in `OrderStatus.next()`, pattern matching for `instanceof` in the filter and exception handler, text blocks for SQL, `var`, `Stream.toList()`, and `ExecutorService` in try-with-resources in the concurrency test.

**170. Record versus class?**
A record is an immutable data carrier: the compiler generates the constructor, accessors, `equals`, `hashCode` and `toString`. It can't extend a class or have mutable fields. JPA entities can't be records because Hibernate needs a no-argument constructor and mutable state.

**171. What is a compact constructor?**
A record constructor without a parameter list that runs before the fields are assigned, used for validation or normalizing. `JwtProperties` validates the secret in one, and `OrderLine` turns a null option list into an empty one.

**172. `Optional`?**
It makes "may be absent" part of a method's return type, so the caller must handle it. It is meant for return values, not fields or parameters. `TenantContext.current()` and `TableLookup.findActive` return one.

**173. `EnumSet` versus `HashSet`?**
`EnumSet` stores enum values as bits, so it is smaller and faster and iterates in declaration order.

**174. Checked versus unchecked exceptions?**
Checked exceptions must be declared or caught, unchecked ones extend `RuntimeException`. Mine are unchecked, because the caller can't recover and the global handler turns them into responses. Spring also rolls back a transaction on unchecked exceptions by default. `QrCodes` wraps `IOException` in `UncheckedIOException`.

**175. How does dependency injection work? Are singletons thread-safe?**
Spring creates the beans and passes each its dependencies through the constructor. Beans are singletons by default, shared by all request threads, so they must hold no per-request state. My services only have `final` references, and the one piece of shared mutable state, the emitter map, uses concurrent collections.

**176. Filter versus interceptor?**
A filter is part of the servlet container and runs before Spring MVC, which is where security belongs. An interceptor runs inside Spring MVC around a controller. `OncePerRequestFilter` guarantees one execution per request even with forwards.

**177. The security filter chain for one admin request?**
The bearer token filter asks my resolver for the token from the cookie, the decoder checks signature and expiry, and the converter builds an authentication with the role. `TenantContextFilter` then sets the tenant. The authorization filter checks the rule for `/api/admin/**`. Then the controller runs, and the tenant is cleared on the way out.

**178. What does `@SpringBootApplication` do?**
It combines `@Configuration`, component scanning from that package down, and auto-configuration, which sets up beans such as the data source, JPA and Tomcat based on what is on the classpath and in the properties.

**179. The stereotype annotations?**
`@Component` marks a bean. `@Service` and `@Repository` are the same with a meaningful name, and `@Repository` adds exception translation. `@Configuration` is a class that defines beans with `@Bean` methods, as `SecurityConfig` does.

**180. How does Spring Data build `findByStatusIn`?**
It parses the method name: `findBy`, property `status`, keyword `In`, and generates the query at startup. If the property doesn't exist the app fails to start. `Sort` and `Limit` parameters add ordering and a row limit.

**181. Entity states and dirty checking?**
Transient (new, unknown to Hibernate), managed (tracked inside a session), detached (session closed) and removed. Changes to a managed entity are detected and written at flush, which is dirty checking.

**182. `@ConfigurationProperties` versus `@Value`?**
`@ConfigurationProperties` binds a group of settings to one typed object, converts types such as `Duration`, and can be validated in one place. `@Value` injects a single value. I use the first for JWT settings and the second for the one base URL.

**183. How does `@Scheduled` work?**
`@EnableScheduling` on the application class turns it on. Spring runs the method on a scheduler thread, by default a single one, so a slow heartbeat would delay the next. `fixedRate = 25_000` means every 25 seconds from start to start.

## Weak spots: say them before the interviewer finds them

- A token still works after logout until it expires (38).
- A signed token with a bad claim would return 500, not 401 (45).
- Roles exist in the data and the token but nothing enforces them (46).
- Kitchen events are sent on the request thread (98).
- No rate limiting, and a table URL works from anywhere (51, 52).
- No idempotency on order placement (70).
- It hasn't been deployed and checked on a real server yet, and the README demo URL is a placeholder.
