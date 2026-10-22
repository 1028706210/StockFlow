# Execution ledger — plan: docs/plans/2026-10-22-p0-implementation.md

User authorization: implement under D:\project (2026-10-22).
Ruling: execute inline without another plan approval — explicit user instruction; scope is the P0 milestone of the staged specification.
Ruling: independent repository in StockFlow, not a worktree from the unrelated unborn parent repository — preserves requested location.
Ruling: standard HTTP Basic with generated local demo accounts for P0 — minimizes authentication machinery; production identity and HTTPS are required before public deployment.
Ruling: private loopback MySQL instance on port 3308, independent dev/test accounts — avoids course credentials and zzyl10.
Ruling: JSON money assertions compare numeric value, not BigDecimal scale — JSON number parsing may normalize trailing zeroes; the monetary value is the API contract.

Task 1: complete — OrderLinesTest RED 6/6 failed; implementation GREEN 6/6 passed.
Task 2: complete — real HTTP/MySQL tests RED 13/13 failed before endpoints; full suite GREEN 19/19 passed, 0 failures/errors/skips. Real MySQL 8.0.30; Java 17; Spring Boot 4.1.0.
Observed and fixed: Spring Security's delegating encoder requires the bcrypt prefix. The authenticated identity test now covers this integration.
Concurrency evidence: 100 requests on 32 workers against stock 10 yielded exactly 10 created orders and 90 stock conflicts; 50 same-key requests on 16 workers yielded one new order and one inventory reservation.
Task 3: in progress — demo UI and CI definition added; final artifact/runtime verification and fresh review pending.
