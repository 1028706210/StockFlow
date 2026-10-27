# StockFlow reliability and business documents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for task execution. Independent file domains may use superpowers:dispatching-parallel-agents.

**Goal:** Complete the six improvements accepted by the user in one continuous delivery.
**Architecture:** Preserve the single Java/MySQL application and native ES modules. Extend existing transactions, idempotency, role checks and audit; use focused modules for document screens and query state. Existing chat changes remain intact.
**Tech Stack:** Java17, Spring Boot4.1, MyBatis, MySQL8, HTML/CSS/ESmodules, Node test runner, Maven.
**Spec:** User accepted the immediately preceding six-item recommendation on 2026-10-27.

## Global Constraints
- Work in D:/project/StockFlow, existing codex/operations-hardening branch/PR1. Never touch zzyl10.
- Preserve existing uncommitted chat functionality. No reset, clean, or destructive migration.
- Existing local database .local credentials remain private and ignored. Only stockflow_test may be cleaned by tests; serialize DB test runs.
- Read/write permissions, identity epoch guards, literal search escaping, idempotent transactions and ordered SKU locks remain enforced.
- Legacy order names stay unknown; never backfill current names as historical truth.
- No real payments/refunds, multiple warehouses, procurement approval, new frontend framework or speculative middleware.
- User explicitly requests continuous execution; implement without repeated approvals. Work in existing checkout to preserve active chat edits and local preview; no new worktree.

## Review Focus
- Legacy order items with null snapshots; product rename/deactivation after order creation.
- Duplicate/concurrent receipt or return confirmation; whole multi-SKU rollback and inventory limits.
- Buyer access to another buyer's orders/returns, exports and detail links.
- Date bounds, timezones, invalid ranges, decimal amounts, CSV escaping and URL query restoration.
- Existing database migration baseline preserves data; failing DB health cannot report ready.

## Task 1: Order history and queries (root)
- [x] RED: snapshot rename/legacy fallback and date/amount/export tests.
- [x] GREEN: order_item adds nullable sku_code/sku_name snapshot; API Item exposes them; new orders save at creation.
- [x] Date filters use ISO YYYY-MM-DD inclusive day from/to, reject reversed; optional order minTotal/maxTotal decimal.
- [x] CSV endpoints GET /api/orders/export, /api/fulfillment/orders/export, /api/inventory/export use current filters/roles, UTF8 BOM, bounded10000 rows with explicit rejection if exceeded.
- [x] For ledger/audit/stocktakes add same date bounds. Existing clients without filters remain compatible.

## Task 2: Receipt and return documents (business agent)
- [x] RED then GREEN real HTTP/MySQL regression tests, coordinate DB runs with root.
- [x] /api/receipts GET paged, POST {requestKey,source,items:[{skuId,quantity}]}; GET /{id}; POST /{id}/confirm|cancel; warehouse/admin only. States DRAFT,CONFIRMED,CANCELLED. 1..10 unique SKU; quantity1..1000000; source required max240; immutable draft payload, idempotent create.
- [x] /api/returns GET paged (buyer own or warehouse all), POST {requestKey,orderId,reason}; GET /{id}; POST /{id}/confirm|cancel. Whole COMPLETED order only, reason1..240; buyer creates/cancels own REQUESTED; warehouse confirms inspected resalable goods; states REQUESTED,CONFIRMED,CANCELLED. Multiple cancelled requests allowed; at most one active/successful per order, enforce under order lock. No refund claims.
- [x] Confirmation locks doc then inventory in ascending SKU, records exactly-once physical changes, version increments, ledger and audit in same transaction. Return uses original order quantities/price/name snapshots where available; never reopens original order.
- [x] Ledger adds receipt_id/return_id nullable references, new type RETURN_IN; receipt confirmed lines use existing INBOUND type and receipt association. Reconciliation includes RETURN_IN. Audit action filters include RECEIPT_CREATE/CONFIRM/CANCEL,RETURN_CREATE/CONFIRM/CANCEL.

## Task 3: Frontend (frontend agent)
- [x] Tests first: URL state roundtrip/bounds, date+amount queries, snapshot labels and new doc retry/identity safety.
- [x] Use hash #page?parameters. Preserve search/status/page/size, date and amount on refresh/back. Existing hash links stay valid; account switches clear buyer-specific query state.
- [x] Add date controls for orders/fulfillment/ledger/audit/stocktakes; amount controls for orders/fulfillment; current-filter CSV download links.
- [x] Add receipts/returns list, create/detail/confirm/cancel UI according to Task2 contracts; preserve request key after uncertain failure. Explain that confirming returns acknowledges received/resalable goods.
- [x] Snapshot-based order rendering, explicit unknown historical name fallback. Preserve chat modules/session bridge and hidden particle controls. Split focused document UI into an ESmodule if useful, no framework.
- [x] Keep source-list context in hash when opening dialogs; same-page links/close restore context and focus.

## Task 4: Runtime and evidence (runtime agent)
- [x] RED/GREEN health ready/down test; database health/readiness separated from process liveness. No public dependency details. Basic bounded request metrics and expiry task counts without credentials.
- [x] Introduce one versioned migration mechanism, preferably Flyway if available. Preserve existing dev/test databases and safe baseline; never baselining arbitrary populated schemas. Root owns V005 snapshot and business agent provides V006 document DDL; integrate all schema paths once known.
- [x] Replace manual replay scripts with version-aware startup and document upgrade instructions; fresh/legacy path regression.
- [x] Add opt-in real MySQL large-data evidence scenario at least10000orders/ledger, query timings/plans for paging/ledger/audit/reconciliation, no invented performance targets. Coordinate test DB exclusively.

## Task 5: Integration and delivery (root)
- [x] Serialize complete backend and frontend tests, fix regressions, package.
- [ ] Browser desktop/mobile receipt/return lifecycle and query restoration/export validation; compare DB/ledger/audit.
- [x] Fresh independent review; fix meaningful findings and rerun appropriate checks.
- [ ] Update README/evidence/QA, commit and push existing PR, verify exact final CI head; leave running preview and truthful boundaries.

Ruling: subagent quota exhausted during implementation; root completed and reviewed integration in place. Existing chat preserved. No new worktree because user requested continuing current project.
Ruling: browser control explicitly disabled in Work settings; no alternate browser automation bypass. Await setting change for real UI acceptance, complete all independent code and API verification.
