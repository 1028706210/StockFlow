# StockFlow P0 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. The user explicitly requested implementation in D:\project; execute inline without another start approval.

**Goal:** Establish an independent, executable Java backend proving transactional stock reservation and idempotent order creation.

**Architecture:** A single Spring Boot application, MyBatis mappers and MySQL/InnoDB. Five business tables, standard Spring Security authentication restricted to local demonstration, and no cache or broker in P0.

**Tech Stack:** Java 17, Spring Boot 4.1.0, MyBatis starter 4.1.0, MySQL 8.0.30 available locally, Maven 3.9.16, JUnit.

**Spec:** ../specs/2026-10-22-stockflow-design.md

## Global constraints

- Work only in D:\project\StockFlow. Do not modify the nursing course projects or use their database credentials.
- No H2 or mocked database acceptance for concurrency or transactions.
- on_hand >= reserved >= 0; at most 10 distinct SKU per order and quantity 1–99 after merging.
- Order totals and audit identity come from the server.
- P0 covers create/read orders; confirmation/cancellation/shipping remain P1, caching/broker remain P2.
- Use an independent local MySQL process on loopback port 3308, with generated ignored credentials and separate dev/test users.
- Local authentication uses standard HTTP Basic with four demo principals; credentials are generated, not committed. A production identity provider and HTTPS deployment are outside P0.
- The new folder is a standalone repository; do not create a worktree from the unrelated, unborn parent repository.

## Review Focus

- Duplicate SKU entries must merge before limits and fingerprinting; normalization must not mutate client input.
- Same request key with reordered lines replays; a changed quantity conflicts.
- A second buyer cannot retrieve another buyer's order; supplied owner/price/state fields are rejected.
- Multi-SKU reservation failure must roll back the order, items, ledger and earlier reservations.
- Duplicate concurrent requests must recover only after the failed transaction ends; deadlock retries cover the entire transaction.

## Task 1 — request normalization

Files: pom.xml, order/OrderLines.java, order/OrderLinesTest.java.
Produces: normalize(List<Line>) and fingerprint(List<Line>).
- [x] Write real JUnit tests for sorted merging, 99-unit boundary, 10-SKU boundary, invalid IDs and stable fingerprints.
- [x] Run the tests against a minimal stub and observe assertion failures.
- [x] Implement normalization and SHA-256 fingerprinting; rerun the whole available suite.

## Task 2 — MySQL, authenticated API and transactional business

Files: StockFlowApplication; api error handling/request IDs; auth configuration; catalog, inventory and order mappers/services/controllers; schema.sql; application properties; local MySQL helper; StockFlowIntegrationTest.
Consumes: normalized lines and fingerprint from Task 1.
Produces: /api/auth/me, /api/skus, /api/inventory, /api/inventory/{skuId}/receipts, /api/orders and /api/orders/{id}.
- [x] Start a fresh isolated MySQL instance and generate private credentials.
- [x] Write integration tests using real HTTP and MySQL. Run them against the initially missing routes to establish RED.
- [x] Implement role/resource authorization, SKU creation, bounded/idempotent inbound receipts, order atomic reservation and post-rollback duplicate recovery.
- [x] Run real 100-request/stock-10 and 50-request/same-key tests, rollback, validation, ownership and inventory-ledger assertions.
- [x] Run the full test suite and package the application.

## Task 3 — usable local handoff

Files: README, HTTP examples, minimal static API demo, CI workflow and local run script.
- [x] Document exact environment setup, generated demo credentials, independent DB boundaries and P0 exclusions.
- [x] Verify startup of the packaged JAR and perform authenticated HTTP smoke checks.
- [x] Run a fresh review of the complete implementation and fix important findings with regression tests.
- [x] Commit only this repository's files and record exact validation evidence.

The plan intentionally does not claim implementation of P1/P2/P3. Continuous integration and local run support begin now; full deployment/benchmark deliverables remain subsequent phases.
