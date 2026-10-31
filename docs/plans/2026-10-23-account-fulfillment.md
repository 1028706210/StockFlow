# StockFlow Identity and Fulfillment Implementation Plan
> REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. User has authorized direct execution.
Goal: implement persisted registration/session login/logout, P1 order lifecycle, and mouse hover feedback.
Architecture: existing modular monolith, standard Spring Security session plus MySQL transactions, native ES Modules.
Spec: docs/specs/2026-10-23-account-fulfillment-design.md

## Global constraints
D:/project/StockFlow only; existing codex branch, preserve data and demo IDs 1..4; no unrelated course edits or credentials in Git.
No new runtime dependencies. Schema owner is root. Agents own disjoint domain files. Tests against stockflow_test only and Maven runs are coordinated serially.
## Review focus
- Cookie state writes require CSRF even before login.
- Replayed state commands and expiration must not double-release or double-ship inventory.
- New accounts must not inherit demo identity IDs or privileged roles.
- Late requests after logout/login must not corrupt the new user's UI.
- Existing databases and old P0 records must upgrade without reset.

## Tasks
### 1. Schema and rollout (root)
Files: schema.sql, db/migrations/V002__accounts_and_fulfillment.sql, tools/Update-LocalDatabase.ps1; docs/progress.md.
- [x] Add non-destructive app_user and cancel_reason migration, verify dev/test metadata and previous row counts.
- [x] Preserve explicit demo IDs, initialize new user auto_increment at100.
### 2. Account backend (auth implementer)
Files: auth/*.java, api/ApiErrors.java only relevant authentication handlers, src/test/java/.../AuthenticationIntegrationTest.java, application.properties session settings.
- [x] Write HTTP registration/session/CSRF/logout tests and report ready before implementing.
- [x] Root runs tests to observe RED; then implement standard Security login/logout and database UserDetailsService.
- [x] Password validation 8..64 characters, max72 UTF8 bytes for BCrypt; lowercase username 3..32 [a-z][a-z0-9_]*.
- [x] Verify no privilege assignment on public register, duplicate username409, invalid credentials401, logout revokes cookie.
### 3. Fulfillment backend (order implementer)
Files: order/*.java, inventory/InventoryMapper.java, new fulfillment/*.java; FulfillmentIntegrationTest.java; test properties expiry disabled.
- [x] Write real HTTP/MySQL lifecycle/replay/race/rollback tests; report ready before implementing.
- [x] Root runs RED; implement locked transaction state changes, ledger operations and bounded scheduled expiration.
- [x] Verify original creation and concurrency behavior plus all new tests.
### 4. Frontend integration and hover (root)
Files: static/index.html, app.js, api-client.mjs, ui-model.mjs, styles.css, src/test/frontend/*.test.mjs.
- [x] API tests RED for same-origin Cookie/CSRF and204, keep race regressions.
- [x] Replace account select with username input; add register form, session restore and real logout.
- [x] Add fulfillment list/status actions and update scope explanations.
- [x] Mouse hover scoped to fine pointer and no reduced-motion, no decorative JS dependencies.
### 5. Verification and delivery (root + fresh reviewer)
- [x] All Node and Java/MySQL tests, package, independent correctness review.
- [x] Real Edge complete lifecycle and hover viewport checks; screenshots + current docs.
- [x] Commit complete feature, keep clean working tree, record exact limitations and results.

## Preflight and execution ledger
Ruling: continue in user-requested D:/project/StockFlow on existing codex/p0-order-inventory; no additional checkout.
Ruling: existing staged design accepted through user's explicit three-part scope response; no repeated approval.
| Tasks | Shared contract | Resolution |
| 1 / 2 | app_user IDs and hash/role columns | Root owns schema; auth owns mapping/seeding; ID1..4 immutable |
| 1 / 3 | customer_order.cancel_reason | Root migration before real tests |
| 2 / 4 | csrf token + Cookie login/logout JSON | Exact routes and forms in spec; browser never sends Basic |
| 3 / 4 | fulfillment routes and cancelReason/buyerId | Existing fields retained, additions only |
| 2 / 3 | Actor.id(Authentication) long | Signature preserved; implementation obtains persisted principal ID |
| 2 / 3 tests | same isolated test DB + Maven target | Root coordinates RED/GREEN runs serially |
| Each task | Test and file scope | Tests first; disjoint domain ownership; no unrelated changes |

## Final execution result — 2026-10-24
All five tasks complete. Java/MySQL:37 passed; frontend:31 passed; package success; real Edge browser acceptance passed. Independent review found expiry starvation; regression RED and cursor fix GREEN plus targeted re-review are documented in docs/progress.md. Current branch is retained locally; no merge or remote publication requested.
