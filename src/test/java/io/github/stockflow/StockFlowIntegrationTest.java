package io.github.stockflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StockFlowIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json=JsonMapper.builder().build();
    record Reply(int status, JsonNode body) {}

    @BeforeEach void resetOnlyTheIsolatedTestSchema() {
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class),
            "Refuse destructive cleanup outside the isolated StockFlow test schema");
        for(var table:List.of("inventory_ledger","order_item","customer_order","inventory","sku"))
            jdbc.update("DELETE FROM "+table);
    }
    Reply request(String user,String method,String path,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(45));
        if(user!=null) {
            var value=Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8));
            b.header("Authorization","Basic "+value);
        }
        if(body!=null)b.header("Content-Type","application/json");
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        var response=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),response.body().isBlank()?json.nullNode():json.readTree(response.body()));
    }
    long sku(String code,String price) throws Exception {
        var r=request("warehouse","POST","/api/skus","{\"code\":\""+code+"\",\"name\":\"Demo "+code+"\",\"price\":"+price+"}");
        assertEquals(201,r.status(),r.body().toString());
        return r.body().get("id").asLong();
    }
    void receipt(long sku,int quantity,String key) throws Exception {
        var r=request("warehouse","POST","/api/inventory/"+sku+"/receipts","{\"operationKey\":\""+key+"\",\"quantity\":"+quantity+"}");
        assertEquals(200,r.status(),r.body().toString());
    }
    String order(String key,long sku,int quantity) {
        return "{\"requestKey\":\""+key+"\",\"items\":[{\"skuId\":"+sku+",\"quantity\":"+quantity+"}]}";
    }
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test void rejectsAnonymousAndExposesARequestId() throws Exception {
        var r=request(null,"GET","/api/skus",null);
        assertEquals(401,r.status());
        assertEquals("UNAUTHENTICATED",r.body().path("code").asText());
        assertFalse(r.body().path("requestId").asText().isBlank());
    }
    @Test void buyerCannotCreateProducts() throws Exception {
        var r=request("buyer","POST","/api/skus","{\"code\":\"NO\",\"name\":\"No\",\"price\":1}");
        assertEquals(403,r.status());
        assertEquals(0,count("sku"));
    }
    @Test void authenticatesTheConfiguredBuyer() throws Exception {
        var r=request("buyer","GET","/api/auth/me",null);
        assertEquals(200,r.status());assertEquals(1,r.body().path("id").asLong());
    }
    @Test void validatesSkuMoneyAndNamesOnTheServer() throws Exception {
        for(var body:List.of("{\"code\":\"A\",\"name\":\"\",\"price\":1}","{\"code\":\"A\",\"name\":\"A\",\"price\":-1}","{\"code\":\"A\",\"name\":\"A\",\"price\":1.001}")){
            assertEquals(400,request("warehouse","POST","/api/skus",body).status());
        }
        assertEquals(0,count("sku"));
    }
    @Test void creatingAnOrderReservesInventoryAndSnapshotsTheServerPrice() throws Exception {
        long id=sku("MUG","12.50");receipt(id,10,"in-1");
        var r=request("buyer","POST","/api/orders",order("buy-1",id,3));
        assertEquals(201,r.status(),r.body().toString());
        assertEquals(0,new BigDecimal("37.50").compareTo(r.body().path("total").decimalValue()));
        assertEquals("PENDING_CONFIRM",r.body().path("status").asText());
        assertEquals(10,jdbc.queryForObject("SELECT on_hand FROM inventory WHERE sku_id=?",Long.class,id));
        assertEquals(3,jdbc.queryForObject("SELECT reserved FROM inventory WHERE sku_id=?",Long.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE operation_type='RESERVE'",Long.class));
    }
    @Test void requestReplayIsIndependentOfItemOrderAndDuplicateLineSplitting() throws Exception {
        long a=sku("A","2.00"),b=sku("B","3.00");receipt(a,10,"a");receipt(b,10,"b");
        var first=request("buyer","POST","/api/orders","{\"requestKey\":\"same\",\"items\":[{\"skuId\":"+a+",\"quantity\":1},{\"skuId\":"+b+",\"quantity\":1},{\"skuId\":"+a+",\"quantity\":1}]}");
        var replay=request("buyer","POST","/api/orders","{\"requestKey\":\"same\",\"items\":[{\"skuId\":"+b+",\"quantity\":1},{\"skuId\":"+a+",\"quantity\":2}]}");
        assertEquals(201,first.status(),first.body().toString());assertEquals(200,replay.status(),replay.body().toString());
        assertEquals(first.body().path("id").asLong(),replay.body().path("id").asLong());
        assertEquals(1,count("customer_order"));assertEquals(2,count("order_item"));
        assertEquals(3,jdbc.queryForObject("SELECT SUM(reserved) FROM inventory",Long.class));
    }
    @Test void sameKeyWithDifferentQuantityConflictsWithoutAnotherReservation() throws Exception {
        long id=sku("A","2");receipt(id,10,"in");
        assertEquals(201,request("buyer","POST","/api/orders",order("key",id,1)).status());
        var r=request("buyer","POST","/api/orders",order("key",id,2));
        assertEquals(409,r.status());assertEquals("IDEMPOTENCY_CONFLICT",r.body().path("code").asText());
        assertEquals(1,count("customer_order"));assertEquals(1,jdbc.queryForObject("SELECT reserved FROM inventory",Long.class));
    }
    @Test void multiSkuShortageRollsBackAllEarlierReservations() throws Exception {
        long a=sku("A","2"),b=sku("B","3");receipt(a,10,"in");
        var r=request("buyer","POST","/api/orders","{\"requestKey\":\"rollback\",\"items\":[{\"skuId\":"+a+",\"quantity\":2},{\"skuId\":"+b+",\"quantity\":1}]}");
        assertEquals(409,r.status());assertEquals("INSUFFICIENT_STOCK",r.body().path("code").asText());
        assertEquals(0,count("customer_order"));assertEquals(0,count("order_item"));
        assertEquals(0,jdbc.queryForObject("SELECT SUM(reserved) FROM inventory",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE operation_type='RESERVE'",Long.class));
    }
    @Test void anotherBuyerCannotReadAnOrderAndCannotSupplyOwnerOrPrice() throws Exception {
        long id=sku("A","2");receipt(id,10,"in");
        var created=request("buyer","POST","/api/orders",order("own",id,1));assertEquals(201,created.status());
        long oid=created.body().path("id").asLong();
        assertEquals(404,request("buyer2","GET","/api/orders/"+oid,null).status());
        assertEquals(200,request("buyer","GET","/api/orders/"+oid,null).status());
        assertEquals(400,request("buyer","POST","/api/orders",order("fake",id,1).replace("\"items\"","\"buyerId\":2,\"total\":0,\"status\":\"SHIPPED\",\"items\"")).status());
        assertEquals(1,count("customer_order"));
    }
    @Test void rejectsInvalidAndMergedOverflowQuantities() throws Exception {
        long id=sku("A","2");receipt(id,200,"in");
        assertEquals(400,request("buyer","POST","/api/orders",order("zero",id,0)).status());
        var r=request("buyer","POST","/api/orders","{\"requestKey\":\"big\",\"items\":[{\"skuId\":"+id+",\"quantity\":60},{\"skuId\":"+id+",\"quantity\":40}]}");
        assertEquals(400,r.status());assertEquals(0,count("customer_order"));
    }
    @Test void receiptRetriesAreIdempotentAndChangedContentConflicts() throws Exception {
        long id=sku("A","2");receipt(id,10,"in");receipt(id,10,"in");
        var changed=request("warehouse","POST","/api/inventory/"+id+"/receipts","{\"operationKey\":\"in\",\"quantity\":11}");
        assertEquals(409,changed.status());
        assertEquals(10,jdbc.queryForObject("SELECT on_hand FROM inventory",Long.class));assertEquals(1,count("inventory_ledger"));
    }
    @Test void oneHundredCompetingRequestsCannotOversellTenUnits() throws Exception {
        long id=sku("A","2");receipt(id,10,"in");
        var pool=Executors.newFixedThreadPool(32);
        try{
            var jobs=new ArrayList<Callable<Reply>>();
            for(int i=0;i<100;i++){final int n=i;jobs.add(()->request("buyer","POST","/api/orders",order("request-"+n,id,1)));}
            int success=0,shortage=0;
            for(var future:pool.invokeAll(jobs)){
                var r=future.get();
                if(r.status()==201)success++;
                else {assertEquals(409,r.status(),r.body().toString());assertEquals("INSUFFICIENT_STOCK",r.body().path("code").asText());shortage++;}
            }
            assertEquals(10,success);assertEquals(90,shortage);
            assertEquals(10,count("customer_order"));assertEquals(10,jdbc.queryForObject("SELECT reserved FROM inventory",Long.class));
            assertEquals(10,jdbc.queryForObject("SELECT SUM(quantity) FROM order_item",Long.class));
            assertEquals(10,jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_ledger WHERE operation_type='RESERVE'",Long.class));
        }finally{pool.shutdownNow();}
    }
    @Test void fiftyConcurrentReplaysCreateOneOrderAndReserveOnce() throws Exception {
        long id=sku("A","2");receipt(id,10,"in");
        var pool=Executors.newFixedThreadPool(16);
        try{
            var jobs=new ArrayList<Callable<Reply>>();
            for(int i=0;i<50;i++)jobs.add(()->request("buyer","POST","/api/orders",order("concurrent-same",id,2)));
            var ids=new HashSet<Long>();int created=0;
            for(var future:pool.invokeAll(jobs)){
                var r=future.get();assertTrue(r.status()==200||r.status()==201,r.body().toString());
                if(r.status()==201)created++;ids.add(r.body().path("id").asLong());
            }
            assertEquals(1,created);assertEquals(1,ids.size());assertEquals(1,count("customer_order"));
            assertEquals(2,jdbc.queryForObject("SELECT reserved FROM inventory",Long.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE operation_type='RESERVE'",Long.class));
        }finally{pool.shutdownNow();}
    }
}

