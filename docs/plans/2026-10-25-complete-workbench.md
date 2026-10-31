# Complete workbench queries and catalog maintenance

> Execution: existing StockFlow feature branch and original directory; preserve all existing uncommitted user work. Use focused parallel backend/frontend work and TDD, with one final independent review.

**Goal:** all primary lists search the complete database; overview uses full aggregates; warehouse can maintain products and inspect a product with its stock and ledger.
**Architecture:** Java17/Spring Boot/MyBatis/MySQL, native HTML/ES modules, no new dependency or schema migration. Add /page endpoints alongside compatible legacy list endpoints. Keep code immutable. No deletion, no public deployment, no course database access.

## Binding API contracts
- GET /api/skus/page?q=&status=all|active|inactive&page=1&size=8 -> Page<Sku>.
- GET /api/inventory/page?q=&status=all|healthy|low|empty&page=1&size=8 -> Page<StockRow> where StockRow {skuId,code,name,price,active,onHand,reserved,available}. Stock statuses: empty available<=0, low 0<available<10, healthy>=10, matches existing page.
- GET /api/orders/page?q=&status=all|PENDING_CONFIRM|CONFIRMED|SHIPPED|COMPLETED|CANCELLED&page=1&size=8 -> Page<Summary>, buyer-owned only. Query matches numeric id or SF-id; warehouse fulfillment also matches buyer numeric id.
- GET /api/fulfillment/orders/page same query -> Page<Summary>, warehouse/admin only.
- Page<T> = {items,total,page,size,totalPages}, totalPages >=1; requested page clamped to last page for consistent filters. size 1..100, page 1..1000000, q <=120, literal %/_/!, Unicode-safe name/code search, all parameterized.
- GET /api/dashboard -> {productCount,onHand,reserved,lowStockCount,orderCount,pendingCount,orderTotal}. Warehouse productCount counts all products, onHand/reserved all inventory, lowStockCount active SKU available<10. Buyer productCount active SKUs; orderCount/pendingCount/orderTotal scope current buyer; orderTotal includes all own orders and UI labels this precisely, never revenue.
- GET /api/skus/{id} -> Sku. Authenticated; buyer may view a known inactive SKU for existing order display, cannot buy it.
- PUT /api/skus/{id} JSON {name,price,active} -> updated Sku, warehouse/admin only. Valid fields as create; name trimmed, code immutable. Old order item prices/totals remain unchanged; new orders respect active flag and current price. No hard delete, no optimistic schema version this iteration.
- GET /api/inventory/{skuId} -> StockRow, warehouse/admin only, 404 if missing.
- Existing GET /api/inventory/ledger adds optional skuId positive exact filter (not substring code); same existing LedgerPage response and original queries retained.

## Frontend tasks
- Replace primary table queries with /page and exact server totals. Debounce search, reset page on filter, retain rows on loading/failure but disable stale actions; guard late responses by session/version.
- Query /api/dashboard for metric values; overview recent rows clearly label recent records. Lists no longer claim last100.
- Selected SKU cache supports cart/receipt without assuming full catalog loaded. Searchable paginated picker enables selection beyond first100 for receipts and cart lines; preserve request-key retry semantics and selected order attempts.
- Product detail side sheet: authenticated product fields; warehouse stock metrics, exact-SKU ledger with load more, edit and receipt; buyer view/choose only. Close/invalidation/identity guards.
- Edit form and explicit shelf-state switch with price/state consequence copy. Preserve existing auth/order flows.
- Tighten report headers and fix empty copy. Keep dark theme, accessible focus, mobile layout.

## Verification
- [x] Backend tests RED then GREEN: >100 rows list/search/aggregates, Unicode/literal matching, status/page validation, buyer isolation/roles, catalog update and immutable historical order prices, inactive purchase rejected, exact SKU ledger.
- [x] Frontend tests RED then GREEN: request races, page filter reset, selected SKU cache beyond page, detail stale close/session, edits/error states and retry attempts.
- [x] node syntax check + full existing tests + package; browser desktop/mobile read + local demo maintenance loop; save screenshots and exact QA.
- [x] Independent review; preserve database and credentials; no GitHub push.

## Decisions
- User accepted proposed three priorities and instructed execution; routine choices made here without further approval.
- Server summaries scoped by current role; counts and sums from database, not downloaded arrays.
- Compatibility endpoints retain previous response shapes for existing clients; current UI uses only new paginated paths for primary lists.
- No test-data deletion or wholesale renaming this iteration.

Completed 2026-10-26: 56 Java/MySQL and 47 frontend tests passed; full product maintenance, picker, second-page buyer selection and mobile drawer accepted in browser. See docs/complete-workbench-qa.md.
