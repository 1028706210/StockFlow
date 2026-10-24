# Execution ledger — plan: docs/plans/2026-10-22-p0-implementation.md

User authorization: implement under D:\project (2026-10-22); continue after the approval service interruption.
Ruling: execute inline without another plan approval — explicit user instruction; scope is the P0 milestone of the staged specification.
Ruling: independent repository in StockFlow, not a worktree from the unrelated unborn parent repository — preserves requested location.
Ruling: standard HTTP Basic with generated local demo accounts for P0 — minimizes authentication machinery; production identity and HTTPS are required before public deployment.
Ruling: private loopback MySQL instance on port 3308, independent dev/test accounts — avoids course credentials and zzyl10.
Ruling: JSON money assertions compare numeric value, not BigDecimal scale — JSON number parsing may normalize trailing zeroes; the monetary value is the API contract.

## Completed plan tasks

Task 1: complete — OrderLinesTest RED 6/6 failed; implementation GREEN 6/6 passed.
Task 2: complete — real HTTP/MySQL tests RED 13/13 failed before endpoints; initial full suite GREEN 19/19 passed. Real MySQL 8.0.30; Java 17; Spring Boot 4.1.0.
Observed and fixed: Spring Security's delegating encoder requires the bcrypt prefix. The authenticated identity test covers this integration.
Concurrency evidence: 100 requests on 32 workers against stock 10 yielded exactly 10 created orders and 90 stock conflicts; 50 same-key requests on 16 workers yielded one new order and one inventory reservation.
Task 3: complete — local demo UI, HTTP examples, run scripts, README and CI definition delivered. Packaged JAR and static assets verified by actual HTTP. Independent code review completed, its important finding corrected, and all 22 tests passed.

## Independent review and regression

Review baseline: local commit 21c7bff420100972667173b0a6d0eb5af757707a.
Important finding: Jackson's float-to-integer coercion accepted fractional order quantities, SKU IDs and receipt quantities before Bean Validation.
Regression RED: 3 failures / 22 tests, 0 errors, because requests expected to return 400 returned 201, 201 and 200.
Fix: set spring.jackson.deserialization.accept-float-as-int=false.
Regression GREEN and package: 22 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
The first resumed test attempt failed to initialize because the private MySQL process had stopped during the interruption; it was restored before observing the actual RED assertions. This initialization error is not counted as a successful regression reproduction.

Deferred minor findings:
- Unsupported HTTP method/media-type exceptions currently reach the generic error handler and can return 500 instead of 405/415. Follow up with narrow handlers and HTTP regression tests.
- Invoke-Local.ps1 resolves Maven before its run branch, so running an existing JAR unnecessarily requires Maven configuration. Resolve it only for test/package in a later small change; the documented local environment includes Maven.

Review rulings: P1 confirmation/cancellation/expiration/shipping and P2 cache/broker reliability remain outside P0. Pending reservations are not automatically released in this milestone. Demo authentication is a local implementation boundary, not production identity. No crash/deadlock fault injection, startup interruption recovery test, browser GUI acceptance or remote CI execution is claimed.

## Final local evidence

Directory: artifacts/p0-reviewed/ (ignored, retained locally).
- red-fractional-input.log: resumed attempt with stopped database; environment failure retained.
- red-fractional-input-db-ready.log: genuine 3-test RED reproduction.
- package.log: final 22-test GREEN and executable JAR build.
- runtime.log: final JAR startup.
- runtime-smoke.json: PASS, health UP, HTML/JS/CSS 200, anonymous 401, order creation 201, replay 200 with same ID, other buyer 404, three fractional field requests 400.
- Rejected-input SQL assertion: on_hand=10, reserved=0, ledger rows=1 (inbound only), order-item rows=0 for the new SKU.
- Final valid order: on_hand=10, reserved=2, available=8.
- JAR SHA-256: 5E0E01C2D334DA26CCAB3C34D8C7036DB6B3F82ADDE6E3C9AF40CFB0845D1311.
- Node syntax check for static app.js: exit 0.
- CI workflow exists but has not been run remotely.

Private credentials, database files, build output and evidence are ignored by Git. Only StockFlow source, tests and handoff documentation are committed. The original course frontend, backend and database were not modified.

## 2026-10-23 frontend workbench upgrade

Authorization: user approved the proposed frontend work and requested a UI plugin, then requested continuation. Bounded upgrade of existing frontend resources; no framework migration, schema changes or P1 endpoints.
Plugin: Product Design index/get-context/critical-overrides/design-qa guidance; inspected existing code and browser baseline. No saved Product Design context existed. Existing code/captured screen was the design target, so no new prototype template or generated asset was required.
Design: retain green palette; add role navigation, latest100 client-paginated tables, modal product/receipt operations, graphical cart, server-backed order detail and responsive layouts. No decorative trend data.
Files: static index.html/styles.css/app.js/ui-model.mjs/api-client.mjs; three Node test files; exact public module routes; one Java asset regression; CI frontend test step; README and QA records/screenshots.

Verification:
- Initial model RED: 10 assertion failures; GREEN: 10 tests passed.
- Static module RED: 1 failure among 23 Java tests (module returned401); exact module paths now public with JavaScript MIME types. Final Java suite:23 passed,0 failures/errors/skips.
- Independent review:3 Important issues found (body-read account race, in-flight attempt reset, stale detail response). Six regressions failed with guards removed and passed after fixes. An initial VM harness was missing document.addEventListener; that harness error is retained but is not claimed as a valid behavioral RED. Corrected evidence: red-races-fixture-corrected.log.
- Minor review findings fixed in scope: displayed SF-order-number search, skip link route preservation, closed mobile sidebar focus exclusion and Escape focus restoration.
- Final acceptance found that a confirmed receipt could reopen the prior SKU when another product was selected. One RED regression reproduced it; the fix resets confirmed operations while preserving unconfirmed attempts. Two added tests cover both cases.
- Final frontend suite:19 passed,0 failures/skips. Node syntax checks pass.
- Final package:artifacts/ui-20261023/package-final.log,BUILD SUCCESS. Earlier packaging failure was a Windows JAR file lock from the old StockFlow Java launcher/child; only those verified processes were stopped.
- Browser using actual final JAR and MySQL: invalid login feedback; warehouse product creation;9-product table search/page2;low-stock filter;receipt24 and original-operation replay still24;buyer two-product order SF-3=173.30;replay same SF-3;detail quantities/prices;SF-3 search;buyer2 empty orders;390px viewport and document width both390;menu open/Escape close;skip-link keeps #orders and focuses main-content.
- Final receipt-reopen browser regression: confirmed1 additional unit of the cup, closed modal, selected cable row; new modal correctly selected the cable with fresh editable inputs.
- Browser error/warn logs after final reload and flows: empty. No all-browser compatibility, formal assistive-technology certification, or live weak-network browser emulation claimed; asynchronous races are covered by Node tests.
- Screenshots:docs/screenshots and artifacts/ui-20261023. Detailed visual review:design-qa.md.
- CI includes frontend tests via actions/setup-node@v7 with Node24 (official setup-node documentation checked); no remote execution claimed.

Review rulings: source review did not judge live appearance/focus, so browser evidence above supplies the bounded visual/keyboard pass. Backend correctness is covered by the23-test real MySQL suite; hosted CI remains unrun. P1/P2, all-history pagination, inactive-product administration and historical product-name snapshots remain outside this upgrade; no such controls are advertised. Production deployment, security audit and broad browser compatibility are not claimed.

The existing P0 deferred HTTP405/415 mapping and Maven resolution script findings remain recorded above. No course project files or credentials were used. Synthetic demo products/orders were added only to stockflow_dev for acceptance.

## 2026-10-24 persisted identity, P1 lifecycle and hover

Authorization: user explicitly selected all three scopes (login/registration/logout, fulfillment and mouse hover), then continued after the interrupted turn. Plan: docs/plans/2026-10-23-account-fulfillment.md; spec: docs/specs/2026-10-23-account-fulfillment-design.md. Changes remain in D:/project/StockFlow on the existing codex branch.

Schema: app_user added with BCrypt hash, unique lowercase username, fixed role and enabled flag; new user IDs start at100. Existing demo IDs1..4 are preserved and seeded only if missing. customer_order.cancel_reason added with an idempotent upgrade; stockflow_dev/test were upgraded, P0 product/order data retained. An initial script error used a literal PowerShell backslash-n; the actual failed upgrade was preserved and fixed to a real newline before verifying both schemas. No course database was used.

Identity: standard Spring Security form-login session, rotation on authentication, HttpOnly/SameSite Lax cookie,30min inactivity timeout and server invalidation on logout. GET CSRF plus protected Cookie writes, fixed BUYER public registration, actual persisted IDs and hashed password, duplicate/concurrent username protection. Explicit Basic remains for local API examples/tests; browser uses Cookie/CSRF. The 30min timeout is configured, not established by waiting30min; invalid/expired-like cookies and replay after real logout were tested.

P1: owned buyer confirm/cancel/complete and warehouse fulfillment list/detail/ship; transaction locks order then visits SKU in ascending order; release and ship are conditional inventory updates plus ledger in the same transaction. Confirmation checks DB time. Default scheduled expiration runs every30sec with at most100 candidates. Failed candidates advance a keyset cursor and are retried after wrap.

Tests and fixes:
- Before production changes,13 new HTTP/MySQL tests failed for missing capabilities,0 initialization errors (artifacts/p1-20261024/red-http.log).
- New frontend request/state tests initially failed, then Cookie/CSRF/form encoding/204/state-action rules passed.
- Two initial Java assertions were corrected: compare JSON money numerically, and expect403 for missing-CSRF anonymous writes while authenticated-resource reads remain401.
- Independent source review found starvation behind100 failed expiry candidates. A real MySQL100-bad-plus101st-healthy regression failed; the rotating bounded cursor fixed it. Targeted re-review found no new important issue.
- Browser acceptance exposed the Spring authentication-factor authority in the public business roles. HTTP login/me regressions failed before filtering only ROLE_ for output; the internal SecurityContext remains unchanged.
- A registration concurrency fixture initially raced first CSRF generation after login; prewarming one token isolates the actual unique-key race without weakening production CSRF or test201/409 expectations.
- Final Java/MySQL suite37 passed (6 auth,8 fulfillment,6 normalization,17 original integration),0 failures/errors/skips. Final frontend suite31 passed. Executable JAR package succeeded.
- Review/runtime evidence: red-review-regressions.log, package-final.log, browser-validation.json and runtime-final.log under ignored artifacts/p1-20261024.

Browser acceptance uses actual packaged JAR, Edge and native MySQL. It creates only dedicated synthetic buyers/products/orders. Existing P0 pending orders are retained and naturally become EXPIRED cancellations. Synthetic expiry confirmation shortens only the new test order deadline, then waits for the actual scheduler. Test harness correction: async waitForFunction returned before the predicate result; replaced by explicit awaited polling of the API. Earlier failed runs are not claimed as acceptance.

Screenshots updated for login/register/workbench/fulfillment/own orders/cart/mobile. Mouse hover uses fine-pointer CSS transitions for buttons/cards/navigation/inputs/login steps; reduced-motion suppresses movement. This is one browser engine and bounded desktop/mobile coverage, not formal accessibility certification or hosted CI.

Remaining boundaries: real payments/logistics, password recovery, administrative account maintenance, Redis/MQ/outbox, all-history server pagination, broad browser coverage and production deployment/security review are not delivered. Public registration grants no warehouse/admin privilege. P0 historical evidence above remains a record of its original implementation, not a statement that P1 is still missing.
