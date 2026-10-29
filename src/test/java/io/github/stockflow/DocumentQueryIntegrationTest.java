package io.github.stockflow;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(DocumentQueryIntegrationTest.QueryConfiguration.class)
class DocumentQueryIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentQueries queries;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final JsonMapper json = JsonMapper.builder().build();
    long firstSku;
    long secondSku;

    @BeforeEach
    void resetOnlyTestSchema() {
        assertEquals("stockflow_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        for (String table : List.of("operation_audit", "inventory_ledger", "return_item", "customer_return",
                "receipt_item", "receipt_document", "stocktake", "order_item", "customer_order", "inventory", "sku")) {
            jdbc.update("DELETE FROM " + table);
        }
        firstSku = sku("QUERY-A");
        secondSku = sku("QUERY-B");
    }

    long sku(String code) {
        jdbc.update("INSERT INTO sku(code,name,price) VALUES(?,'当前商品名称',99.99)", code);
        return jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, code);
    }

    List<Long> seedReceipts(int count) {
        var ids = new ArrayList<Long>();
        for (int index = 0; index < count; index++) {
            String key = "query-receipt-" + index;
            String source = index % 4 == 0 ? "供货%_!" : "普通供货";
            String status = List.of("DRAFT", "CONFIRMED", "CANCELLED").get(index % 3);
            jdbc.update("INSERT INTO receipt_document(actor_id,request_key,payload_hash,source,status) VALUES(3,?,'test',?,?)",
                    key, source, status);
            long id = jdbc.queryForObject("SELECT id FROM receipt_document WHERE request_key=?", Long.class, key);
            // 故意逆序插入，验证列表仍按 SKU 排序，且使用各单据自己的数量和快照。
            jdbc.update("INSERT INTO receipt_item(receipt_id,sku_id,sku_code,sku_name,quantity) VALUES(?,?,'OLD-B','入库快照乙',?)",
                    id, secondSku, 2 + index);
            jdbc.update("INSERT INTO receipt_item(receipt_id,sku_id,sku_code,sku_name,quantity) VALUES(?,?,'OLD-A','入库快照甲',?)",
                    id, firstSku, 1 + index);
            ids.add(id);
        }
        return ids;
    }

    List<Long> seedReturns(int count) {
        var ids = new ArrayList<Long>();
        for (int index = 0; index < count; index++) {
            long buyer = index % 2 + 1;
            String key = "query-return-" + index;
            jdbc.update("INSERT INTO customer_order(buyer_id,request_key,payload_hash,status,total,expires_at) VALUES(?,?,'test','COMPLETED',10,DATE_ADD(NOW(),INTERVAL 1 DAY))",
                    buyer, key);
            long order = jdbc.queryForObject("SELECT id FROM customer_order WHERE request_key=?", Long.class, key);
            String reason = index % 4 == 0 ? "退回%_!" : "普通退回";
            String status = List.of("REQUESTED", "CONFIRMED", "CANCELLED").get(index % 3);
            jdbc.update("INSERT INTO customer_return(order_id,buyer_id,request_key,payload_hash,reason,status) VALUES(?,?,?,'test',?,?)",
                    order, buyer, key, reason, status);
            long id = jdbc.queryForObject("SELECT id FROM customer_return WHERE request_key=?", Long.class, key);
            jdbc.update("INSERT INTO return_item(return_id,sku_id,sku_code,sku_name,quantity,unit_price) VALUES(?,?,NULL,NULL,?,2.50)",
                    id, secondSku, 2 + index % 20);
            jdbc.update("INSERT INTO return_item(return_id,sku_id,sku_code,sku_name,quantity,unit_price) VALUES(?,?,'OLD-A','原订单快照',?,1.25)",
                    id, firstSku, 1 + index % 20);
            ids.add(id);
        }
        return ids;
    }

    HttpResponse<String> request(String user, String path) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (user != null) {
            String credential = user + ":" + System.getenv("STOCKFLOW_DEMO_PASSWORD");
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
        }
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode get(String user, String path) throws Exception {
        var response = request(user, path);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    JsonNode page(String user, String path, int itemCount) throws Exception {
        queries.start();
        try {
            var result = get(user, path);
            var sql = queries.finish();
            assertEquals(itemCount == 0 ? 2 : 3, sql.size(), sql.toString());
            if (itemCount > 0) {
                String itemsSql = sql.get(2);
                assertTrue(itemsSql.contains(" IN ("), itemsSql);
                assertEquals(itemCount, itemsSql.chars().filter(c -> c == '?').count(), itemsSql);
            }
            assertEquals(itemCount, result.path("items").size());
            return result;
        } finally {
            queries.finish();
        }
    }

    void assertDocuments(JsonNode page, List<Long> expectedIds, List<Long> allIds, boolean receipt) {
        var actualIds = new ArrayList<Long>();
        for (var document : page.path("items")) {
            long id = document.path("id").asLong();
            actualIds.add(id);
            int index = allIds.indexOf(id);
            assertTrue(index >= 0);
            var items = document.path("items");
            assertEquals(2, items.size());
            assertEquals(firstSku, items.get(0).path("skuId").asLong());
            assertEquals(secondSku, items.get(1).path("skuId").asLong());
            assertEquals(1 + (receipt ? index : index % 20), items.get(0).path("quantity").asInt());
            assertEquals(2 + (receipt ? index : index % 20), items.get(1).path("quantity").asInt());
            assertEquals("OLD-A", items.get(0).path("skuCode").asText());
            assertEquals(receipt ? "入库快照甲" : "原订单快照", items.get(0).path("skuName").asText());
            assertFalse(items.get(0).has("receiptId"));
            assertFalse(items.get(0).has("returnId"));
            if (receipt) {
                assertEquals("入库快照乙", items.get(1).path("skuName").asText());
            } else {
                assertEquals(index % 2 + 1, document.path("buyerId").asInt());
                assertTrue(items.get(1).path("skuName").isNull());
                assertTrue(items.get(1).path("skuCode").isNull());
                assertEquals(0, new java.math.BigDecimal("1.25").compareTo(items.get(0).path("unitPrice").decimalValue()));
                assertEquals(0, new java.math.BigDecimal("2.50").compareTo(items.get(1).path("unitPrice").decimalValue()));
            }
        }
        assertEquals(expectedIds, actualIds);
    }

    List<Long> descending(List<Long> ids) {
        var result = new ArrayList<>(ids);
        Collections.reverse(result);
        return result;
    }

    @Test
    void receiptPagesUseThreeSqlStatementsAtOneEightAndOneHundredRows() throws Exception {
        var ids = seedReceipts(101);
        var ordered = descending(ids);
        for (int size : List.of(100, 8, 1)) {
            var result = page("warehouse", "/api/receipts?size=" + size, size);
            assertEquals(101, result.path("total").asLong());
            assertDocuments(result, ordered.subList(0, size), ids, true);
            assertEquals(get("warehouse", "/api/receipts/" + ordered.get(0)), result.path("items").get(0));
        }
        var last = page("admin", "/api/receipts?size=100&page=999", 1);
        assertEquals(2, last.path("page").asInt());
        assertEquals(2, last.path("totalPages").asInt());
        assertDocuments(last, List.of(ids.get(0)), ids, true);
    }

    @Test
    void returnPagesKeepBuyerScopeSnapshotsAndThreeSqlStatementsAtMaximumSize() throws Exception {
        var ids = seedReturns(101);
        var ordered = descending(ids);
        for (int size : List.of(100, 8, 1)) {
            var result = page("warehouse", "/api/returns?size=" + size, size);
            assertEquals(101, result.path("total").asLong());
            assertDocuments(result, ordered.subList(0, size), ids, false);
            assertEquals(get("warehouse", "/api/returns/" + ordered.get(0)), result.path("items").get(0));
        }
        for (int buyer = 1; buyer <= 2; buyer++) {
            var ownIds = new ArrayList<Long>();
            for (int index = 0; index < ids.size(); index++) {
                if (index % 2 + 1 == buyer) ownIds.add(ids.get(index));
            }
            String user = buyer == 1 ? "buyer" : "buyer2";
            var result = page(user, "/api/returns?size=100", ownIds.size());
            assertEquals(ownIds.size(), result.path("total").asLong());
            assertDocuments(result, descending(ownIds), ids, false);
        }
        var last = page("admin", "/api/returns?size=100&page=999", 1);
        assertEquals(2, last.path("page").asInt());
        assertDocuments(last, List.of(ids.get(0)), ids, false);
        assertEquals(404, request("buyer2", "/api/returns/" + ids.get(0)).statusCode());
    }

    @Test
    void receiptFiltersKeepLiteralWildcardsAndStatusPagination() throws Exception {
        var ids = seedReceipts(13);
        String keyword = URLEncoder.encode("%_!", StandardCharsets.UTF_8);
        var result = page("warehouse", "/api/receipts?q=" + keyword + "&status=DRAFT&size=1&page=2", 1);
        assertEquals(2, result.path("total").asInt());
        assertEquals(2, result.path("page").asInt());
        assertDocuments(result, List.of(ids.get(0)), ids, true);
    }

    @Test
    void returnFiltersApplyBeforeFetchingOnlyTheAuthorizedPageItems() throws Exception {
        var ids = seedReturns(13);
        String keyword = URLEncoder.encode("%_!", StandardCharsets.UTF_8);
        var result = page("buyer", "/api/returns?q=" + keyword + "&status=REQUESTED&size=1&page=2", 1);
        assertEquals(2, result.path("total").asInt());
        assertDocuments(result, List.of(ids.get(0)), ids, false);
        var empty = page("buyer2", "/api/returns?q=" + keyword + "&status=REQUESTED", 0);
        assertEquals(0, empty.path("total").asInt());
    }

    @Test
    void emptyPagesSkipItemSqlAndKeepPaginationAndAccessRules() throws Exception {
        for (String path : List.of("/api/receipts", "/api/returns")) {
            var result = page("warehouse", path + "?page=999", 0);
            assertEquals(0, result.path("total").asInt());
            assertEquals(1, result.path("page").asInt());
            assertEquals(1, result.path("totalPages").asInt());
            assertEquals(401, request(null, path).statusCode());
            assertEquals(400, request("warehouse", path + "?size=101").statusCode());
            assertEquals(400, request("warehouse", path + "?page=0").statusCode());
        }
        assertEquals(403, request("buyer", "/api/receipts").statusCode());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryConfiguration {
        @Bean DocumentQueries documentQueries() { return new DocumentQueries(); }
    }

    // 仅在测试中计数实际 JDBC prepare；认证和夹具 SQL 不纳入单据查询次数。
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class DocumentQueries implements Interceptor {
        private final ConcurrentLinkedQueue<String> statements = new ConcurrentLinkedQueue<>();
        private volatile boolean enabled;

        void start() {
            statements.clear();
            enabled = true;
        }

        List<String> finish() {
            enabled = false;
            return List.copyOf(statements);
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            if (enabled) {
                String sql = ((StatementHandler) invocation.getTarget()).getBoundSql().getSql()
                        .replaceAll("\\s+", " ").trim();
                String normalized = sql.toLowerCase(java.util.Locale.ROOT);
                if (normalized.startsWith("select ") && List.of("receipt_document", "receipt_item", "customer_return", "return_item")
                        .stream().anyMatch(table -> normalized.contains("from " + table + " "))) {
                    statements.add(sql);
                }
            }
            return invocation.proceed();
        }
    }
}
