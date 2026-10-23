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
