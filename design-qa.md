# StockFlow UI verification

## 2026-10-23 Canva / Figma visual polish

Latest visual sources: [Figma overview](https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=3-203), [Figma login](https://www.figma.com/design/OVp7EQbg2Dt7RwRphNCWJz?node-id=3-342), and [Canva draft](https://canva.link/7sbb7attc1fboz8). Canva text was verified; its remote thumbnail was blank and is not claimed as a visually validated source.

Scope: stylesheet presentation, embedded favicon, screenshots and design documentation. Business JavaScript, Java and database schema are unchanged. Editable Figma screens contain native layers, seven component instances, Noto Sans SC text and no image-filled nodes.

Fresh validation: 19 frontend tests passed; 23 Java tests (including 17 real HTTP/MySQL integration cases) passed; Maven package succeeded. Final Edge headless check covered 1440x960 login/overview/inventory/order draft and 390x844 cart/orders, visible overview text >=12px, table cells 14px, receipt dialog fit, navigation Escape behavior and buyer2 empty orders. Browser evidence is in ignored artifacts/ui-canva-figma-20261023/browser-validation.json.

The browser draft check used existing synthetic products and did not submit business writes. The first console pass exposed unauthorized default favicon requests; an embedded SF favicon repaired the source. The final pass checks console errors again. This is one browser engine and two viewport sizes, not a formal accessibility certification or hosted CI run.

Current browser captures: docs/screenshots/login.png, workbench.png, create-order.png, mobile-order.png. Figma export previews and state ledger are preserved under ignored artifacts/ui-canva-figma-20261023. Original implementation evidence below describes the earlier behavioral acceptance and is retained as history.

## Earlier workbench implementation record


Date: 2026-10-23 (Asia/Taipei). Scope: existing StockFlow frontend upgrade requested by the user.
final result: passed

## Visual grounding and comparison

Source visual truth: artifacts/ui-20261023/ui-before-desktop.png, captured from the existing API demo. This was a functional redesign, not a pixel-for-pixel clone.
Rendered implementation: docs/screenshots/workbench.png; docs/screenshots/create-order.png; docs/screenshots/mobile-order.png; artifacts/ui-20261023/ui-login-desktop.png and ui-order-detail-desktop.png.
Combined full-view comparisons: artifacts/ui-20261023/ui-entry-comparison.png and ui-workbench-comparison.png. Both sides were opened together for review.
Desktop CSS viewport:1440x960. Native screenshot images vary with browser surface scale:baseline1295x863,login/cart/detail1440x960,overview/inventory1425x950. Comparison sheets contain each view proportionally inside1000x667,side by side with captions; no claim of pixel-precise fidelity or a fixed native device scale.
Mobile viewport/image:390x844. Read-only DOM measured document clientWidth=390 and scrollWidth=390 on orders and cart.
States:baseline public API form;new public login;authenticated warehouse overview/inventory;buyer draft/success/detail;buyer2 empty orders;mobile orders/cart.
The baseline and final authenticated overview have intentionally different information architecture and different synthetic data. These are accepted design changes, not measured visual drift. Public-entry comparison uses the public state on both sides.
Focused regions reviewed at full screenshot size:table labels and prices,cart rows/amount,detail dialog and mobile menu. Separate crops were unnecessary because these were readable in individual full-resolution captures.

## Required surfaces

- Typography:system Chinese-capable font stack, clear heading/body/metadata hierarchy; increased secondary text contrast and table-label sizing during implementation. Native screenshots checked for clipping and wrapping. No formal contrast certification claimed.
- Spacing/layout:220px desktop navigation, consistent content inset, table/card spacing and aligned numeric columns;modal content fits desktop.390px cart switches to one column, tables scroll within their panel instead of widening the document.
- Colors/tokens:retained the existing green direction with unified brand/line/semantic variables;green primary action and reservation card,amber low-stock,neutral unavailable stock. Values are actual API data.
- Assets/image quality:no product photos or illustrations were required. Branding is a text wordmark;no raster placeholder assets or fabricated charts. Screenshots are browser captures.
- Copy/content:ordinary task labels replace raw JSON;explicit local demo,latest100 scope,price preview vs server result and pending-reservation limitations. No unsupported fulfillment action is presented.

## Interaction evidence

Passed using the in-app browser and actual MySQL-backed JAR:
1.Invalid login then warehouse login.
2.Create UI-CUP-20261023;search by name;9 records with page2 accessible;stock filter.
3.Inbound24,replay same operation still24;new-operation reset disabled while submitting.
4.Buyer cart:cup2 plus pen3;preview and server total173.30;replay returns SF-3.
5.Order detail contains both line prices/quantities;search SF-3 matches visible order.
6.Switch buyer2:0 own orders and no previous success/cart state.
7.Mobile menu opens,Escape closes and returns focus;closed sidebar hidden from accessibility tree.
8.Skip link focuses main-content while preserving #orders.
9.Final build receipt-reopen check selects cable after a confirmed cup receipt.
10.Error/warn console entries after final browser pass:0.

## Comparison history and fixes

- Existing baseline:form cards and raw request output. Intentional improvement:role-based navigation, business tables and graphical order entry.
- Initial implementation:secondary text was too faint and mobile sidebar only translated offscreen. Strengthened secondary colors/table labels;closed drawer now uses visibility:hidden;desktop/mobile captures reviewed after changes.
- Independent review:session-body-read race,reset-while-submitting andstale detail response fixed with six RED-to-GREEN asynchronous regressions.
- Keyboard/search review:SF-prefix search,skip-link routing andmobile Escape focus fixed andverified inbrowser.
- Final receipt selection issue:closed confirmed operations now begin a fresh selected-SKU operation;unconfirmed operations retain their original key.19 frontend tests pass andthe final browser selection check passes.
- No actionable visual P0/P1/P2 issue remains in this tested scope.

## Limits and follow-up

Current workbench uses native ES Modules and is served by Spring Boot. No separate Vue frontend/build step was introduced. Pagination covers the most recent100 records,not the whole database. P1 confirmation/cancellation/expiration/shipping remains unavailable.
A real interrupted-network browser test,formal screen-reader pass,other browser engines andhosted CI are not covered. The asynchronous account/attempt/detail races were exercised with deferred-response Node tests.
Synthetic development data remains for demo purposes;passwords/database files are ignored by Git.
