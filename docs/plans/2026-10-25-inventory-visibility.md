# StockFlow inventory visibility and interface polish

Goal: finish a cohesive dark warehouse workspace with real inventory ledger and low-stock alerts.
Architecture: reuse existing transactional inventory_ledger; add read-only warehouse APIs with keyset pagination, no schema migration, no new dependencies. Keep original framework and authentication. Existing dark changes are user work to preserve.

## Contracts
GET /api/inventory/ledger?q=&type=&beforeId=&limit=20 -> {items:[{id,skuId,code,name,orderId,actorId,operationType,quantity,createdAt,onHandDelta,reservedDelta}],nextCursor}. Allowed types INBOUND,RESERVE,RELEASE,SHIP; blank means all. Sort id DESC. Limit 1..100, q <=120, beforeId positive.
GET /api/inventory/alerts?q=&threshold=10&beforeId=&limit=20 -> {items:[{skuId,code,name,onHand,reserved,available}],nextCursor,total,threshold}. Active SKUs with available < threshold. threshold 1..1000000. Sort skuId DESC. total counts the complete matched set, not the page. Same q/limit/cursor constraints. Treat % and _ in query as literal characters.
Only warehouse/admin roles; buyer denied. No actor credentials or operation keys exposed.

## Tasks
- [x] Backend: query DTOs, parameterized SQL, validation, API and MySQL tests for search, paging, permissions and full-set alerts beyond 100 latest products. Existing ledger writes unchanged.
- [x] Frontend: two navigation pages; backend filtering/paging with stale-response guards; ledger display explicit physical/reserved deltas; alert threshold control and one-click receipt; loading/error/empty states.
- [x] Visual polish: disciplined dark slate surfaces, mint accent, refined cards/type/toolbar/empty states; mobile and long-account layouts. Retain IDs and old flows, no new framework.
- [x] Verify: frontend tests + Maven package; real browser desktop/mobile/login/ledger/alerts/filter/receipt checks; save screenshots and update README with exact boundaries.

Review focus: stale identity responses, threshold equality excluded, empty results after filter reset, paging without duplicates, no unauthorized ledger access. No notifications/email/production deployment or new warehouse model. User explicitly requests implementation in one pass; routine design choices are delegated to agent.
Completed 2026-10-25: 44 Java/MySQL tests, 35 frontend tests, JS syntax check and package passed; independent review plus desktop/mobile browser acceptance recorded in docs/inventory-visibility-qa.md.
