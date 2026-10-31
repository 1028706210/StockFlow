package io.github.stockflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
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
class FulfillmentIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json=JsonMapper.builder().build();
    record Reply(int status,JsonNode body) {}

    @BeforeEach void resetOnlyTheIsolatedTestSchema() {
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        for(var table:List.of("operation_audit","inventory_ledger","return_item","customer_return","receipt_item","receipt_document","stocktake","order_item","customer_order","inventory","sku"))
            jdbc.update("DELETE FROM "+table);
    }
    Reply request(String user,String method,String path,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(45));
        if(user!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString(
            (user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8)));
        if(body!=null)b.header("Content-Type","application/json");
        var response=client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),response.body().isBlank()?json.nullNode():json.readTree(response.body()));
    }
    long sku(String code) throws Exception {
        var r=request("warehouse","POST","/api/skus","{\"code\":\""+code+"\",\"name\":\"Product\",\"price\":2.50}");
        assertEquals(201,r.status(),r.body().toString());
        long id=r.body().path("id").asLong();
        assertEquals(200,request("warehouse","POST","/api/inventory/"+id+"/receipts","{\"operationKey\":\"initial\",\"quantity\":10}").status());
        return id;
    }
    long create(String key,long... skus) throws Exception {
        var items=new ArrayList<String>();
        for(long sku:skus)items.add("{\"skuId\":"+sku+",\"quantity\":2}");
        var r=request("buyer","POST","/api/orders","{\"requestKey\":\""+key+"\",\"items\":["+String.join(",",items)+"]}");
        assertEquals(201,r.status(),r.body().toString());
        return r.body().path("id").asLong();
    }
    Reply buyerCommand(long id,String action) throws Exception {return request("buyer","POST","/api/orders/"+id+"/"+action,null);}
    Reply ship(long id) throws Exception {return request("warehouse","POST","/api/fulfillment/orders/"+id+"/ship",null);}
    void status(Reply reply,String state) {assertEquals(200,reply.status(),reply.body().toString());assertEquals(state,reply.body().path("status").asText());}
    long scalar(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    void stock(long id,long onHand,long reserved) {
        assertEquals(onHand,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",id));
        assertEquals(reserved,scalar("SELECT reserved FROM inventory WHERE sku_id=?",id));
    }
    long ledger(long order,String type){return scalar("SELECT COUNT(*) FROM inventory_ledger WHERE order_id=? AND operation_type=?",order,type);}
    void expireBatch() throws Exception {
        var expiration=context.getBean("orderExpiration");
        expiration.getClass().getMethod("expireBatch").invoke(expiration);
    }

    @Test void fullLifecyclePreservesSnapshotsAndShipsExactlyOnceAcrossReplays() throws Exception {
        long sku=sku("LIFE"),id=create("life",sku);
        var detail=request("buyer","GET","/api/orders/"+id,null);
        assertEquals(1,detail.body().path("buyerId").asLong());
        assertTrue(detail.body().has("cancelReason"));assertTrue(detail.body().path("cancelReason").isNull());
        assertEquals(0,new BigDecimal("5.00").compareTo(detail.body().path("total").decimalValue()));
        assertEquals(409,ship(id).status());assertEquals(409,buyerCommand(id,"complete").status());
        status(buyerCommand(id,"confirm"),"CONFIRMED");status(buyerCommand(id,"confirm"),"CONFIRMED");
        stock(sku,10,2);assertEquals(409,buyerCommand(id,"cancel").status());
        var listed=request("warehouse","GET","/api/fulfillment/orders",null);
        assertEquals(200,listed.status());assertEquals(id,listed.body().get(0).path("id").asLong());
        assertEquals(1,listed.body().get(0).path("buyerId").asLong());assertTrue(listed.body().get(0).has("cancelReason"));
        var warehouseDetail=request("warehouse","GET","/api/fulfillment/orders/"+id,null);
        assertEquals(200,warehouseDetail.status());assertEquals(2,warehouseDetail.body().path("items").get(0).path("quantity").asInt());
        status(ship(id),"SHIPPED");status(ship(id),"SHIPPED");stock(sku,8,0);assertEquals(1,ledger(id,"SHIP"));
        status(buyerCommand(id,"confirm"),"SHIPPED");
        status(buyerCommand(id,"complete"),"COMPLETED");status(buyerCommand(id,"complete"),"COMPLETED");
        status(ship(id),"COMPLETED");status(buyerCommand(id,"confirm"),"COMPLETED");
        stock(sku,8,0);assertEquals(1,ledger(id,"SHIP"));assertEquals(0,ledger(id,"RELEASE"));
    }
    @Test void cancellationReleasesOnceAndRemainsVisibleInBuyerSummary() throws Exception {
        long sku=sku("CANCEL"),id=create("cancel",sku);
        var cancelled=buyerCommand(id,"cancel");status(cancelled,"CANCELLED");
        assertEquals("USER",cancelled.body().path("cancelReason").asText());
        status(buyerCommand(id,"cancel"),"CANCELLED");stock(sku,10,0);assertEquals(1,ledger(id,"RELEASE"));
        assertEquals(409,buyerCommand(id,"confirm").status());assertEquals(409,ship(id).status());assertEquals(409,buyerCommand(id,"complete").status());
        var list=request("buyer","GET","/api/orders",null);
        assertEquals("USER",list.body().get(0).path("cancelReason").asText());
    }
    @Test void ownershipAndRoleChecksPreventStateChangesAndHideOtherBuyerOrders() throws Exception {
        long sku=sku("ACCESS"),id=create("access",sku);
        for(var action:List.of("confirm","cancel","complete")) {
            assertEquals(404,request("buyer2","POST","/api/orders/"+id+"/"+action,null).status());
            assertEquals(403,request("warehouse","POST","/api/orders/"+id+"/"+action,null).status());
        }
        assertEquals(403,request("buyer","GET","/api/fulfillment/orders",null).status());
        assertEquals(403,request("buyer","GET","/api/fulfillment/orders/"+id,null).status());
        assertEquals(403,request("buyer","POST","/api/fulfillment/orders/"+id+"/ship",null).status());
        // Anonymous writes still require CSRF; the security filter rejects a missing token first.
        assertEquals(403,request(null,"POST","/api/orders/"+id+"/confirm",null).status());
        assertEquals(401,request(null,"GET","/api/fulfillment/orders",null).status());
        assertEquals(404,request("warehouse","GET","/api/fulfillment/orders/9223372036854775807",null).status());
        status(buyerCommand(id,"confirm"),"CONFIRMED");
        status(request("admin","POST","/api/fulfillment/orders/"+id+"/ship",null),"SHIPPED");
        stock(sku,8,0);
    }
    @Test void competingConfirmAndCancelChooseOneStateWithoutDoubleRelease() throws Exception {
        long sku=sku("RACE"),id=create("race",sku);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(12);
        try {
            var futures=new ArrayList<Future<Reply>>();
            for(int i=0;i<12;i++) {String action=i%2==0?"confirm":"cancel";futures.add(pool.submit(()->{start.await();return buyerCommand(id,action);}));}
            start.countDown();int success=0,conflicts=0;
            for(var future:futures) {var r=future.get(50,TimeUnit.SECONDS);if(r.status()==200)success++;else {assertEquals(409,r.status(),r.body().toString());conflicts++;}}
            assertEquals(6,success);assertEquals(6,conflicts);
            String state=jdbc.queryForObject("SELECT status FROM customer_order WHERE id=?",String.class,id);
            assertTrue(Set.of("CONFIRMED","CANCELLED").contains(state));
            stock(sku,10,state.equals("CANCELLED")?0:2);assertEquals(state.equals("CANCELLED")?1:0,ledger(id,"RELEASE"));
        } finally {pool.shutdownNow();}
    }
    @Test void expirationUsesDatabaseDeadlineAndRepeatedBatchesReleaseOnlyExpiredPending() throws Exception {
        long sku=sku("EXPIRE"),expired=create("expired",sku),future=create("future",sku),confirmed=create("confirmed",sku);
        status(buyerCommand(confirmed,"confirm"),"CONFIRMED");
        jdbc.update("UPDATE customer_order SET expires_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id IN (?,?)",expired,confirmed);
        assertEquals(409,buyerCommand(expired,"confirm").status());stock(sku,10,6);
        expireBatch();expireBatch();
        var r=request("buyer","GET","/api/orders/"+expired,null);status(r,"CANCELLED");assertEquals("EXPIRED",r.body().path("cancelReason").asText());
        status(buyerCommand(expired,"cancel"),"CANCELLED");assertEquals(1,ledger(expired,"RELEASE"));stock(sku,10,4);
        status(request("buyer","GET","/api/orders/"+future,null),"PENDING_CONFIRM");
        status(request("buyer","GET","/api/orders/"+confirmed,null),"CONFIRMED");assertEquals(0,ledger(confirmed,"RELEASE"));
    }
    @Test void shipFailureOnSecondSkuRollsBackFirstSkuLedgerAndOrder() throws Exception {
        long a=sku("SHIP-A"),b=sku("SHIP-B"),id=create("ship-rollback",a,b);
        status(buyerCommand(id,"confirm"),"CONFIRMED");
        jdbc.update("UPDATE inventory SET reserved=0 WHERE sku_id=?",b);
        assertEquals(409,ship(id).status());stock(a,10,2);stock(b,10,0);assertEquals(0,ledger(id,"SHIP"));
        status(request("buyer","GET","/api/orders/"+id,null),"CONFIRMED");
        jdbc.update("UPDATE inventory SET reserved=2 WHERE sku_id=?",b);
        status(ship(id),"SHIPPED");stock(a,8,0);stock(b,8,0);assertEquals(2,ledger(id,"SHIP"));
    }
    @Test void failedExpiredOrdersDoNotStarveLaterHealthyOrdersAcrossBatches() throws Exception {
        long brokenSku=sku("EXP-BROKEN"),healthySku=sku("EXP-HEALTHY");
        // Deliberately inconsistent fixtures model old orders whose reserved stock was damaged.
        var blocked=new ArrayList<Object[]>();
        for(int i=0;i<100;i++)blocked.add(new Object[]{"blocked-"+i});
        jdbc.batchUpdate("INSERT INTO customer_order(buyer_id,request_key,payload_hash,status,total,expires_at) VALUES(1,?,REPEAT('a',64),'PENDING_CONFIRM',5.00,DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 2 MINUTE))",blocked);
        jdbc.update("INSERT INTO order_item(order_id,sku_id,quantity,unit_price) SELECT id,?,2,2.50 FROM customer_order WHERE request_key LIKE 'blocked-%'",brokenSku);
        long healthy=create("healthy-after-blocked",healthySku);
        jdbc.update("UPDATE customer_order SET expires_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 MINUTE) WHERE id=?",healthy);
        for(int attempt=0;attempt<3;attempt++){
            expireBatch();
            if("CANCELLED".equals(jdbc.queryForObject("SELECT status FROM customer_order WHERE id=?",String.class,healthy)))break;
        }
        var result=request("buyer","GET","/api/orders/"+healthy,null);
        status(result,"CANCELLED");assertEquals("EXPIRED",result.body().path("cancelReason").asText());
        stock(healthySku,10,0);assertEquals(1,ledger(healthy,"RELEASE"));
        stock(brokenSku,10,0);
        assertEquals(100,scalar("SELECT COUNT(*) FROM customer_order WHERE request_key LIKE 'blocked-%' AND status='PENDING_CONFIRM'"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE sku_id=? AND operation_type='RELEASE'",brokenSku));
    }
    @Test void releaseFailureOnSecondSkuRollsBackFirstSkuLedgerAndOrder() throws Exception {
        long a=sku("REL-A"),b=sku("REL-B"),id=create("release-rollback",a,b);
        jdbc.update("UPDATE inventory SET reserved=0 WHERE sku_id=?",b);
        assertEquals(409,buyerCommand(id,"cancel").status());stock(a,10,2);stock(b,10,0);assertEquals(0,ledger(id,"RELEASE"));
        status(request("buyer","GET","/api/orders/"+id,null),"PENDING_CONFIRM");
        jdbc.update("UPDATE inventory SET reserved=2 WHERE sku_id=?",b);
        status(buyerCommand(id,"cancel"),"CANCELLED");stock(a,10,0);stock(b,10,0);assertEquals(2,ledger(id,"RELEASE"));
    }
}
