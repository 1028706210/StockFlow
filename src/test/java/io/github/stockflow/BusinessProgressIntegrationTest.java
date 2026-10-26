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
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BusinessProgressIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired io.github.stockflow.order.OrderService orderService;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json=JsonMapper.builder().build();
    record Reply(int status,JsonNode body) {}
    @BeforeEach void resetOnlyTestSchema(){
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        for(var table:List.of("operation_audit","inventory_ledger","stocktake","order_item","customer_order","inventory","sku"))jdbc.update("DELETE FROM "+table);
    }
    Reply request(String user,String method,String path,String body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        if(user!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8)));
        if(body!=null)b.header("Content-Type","application/json");
        var r=client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(),r.body().isBlank()?json.nullNode():json.readTree(r.body()));
    }
    JsonNode ok(String user,String path)throws Exception{
        var r=request(user,"GET",path,null);assertEquals(200,r.status(),r.body().toString());return r.body();
    }
    String q(String text){return URLEncoder.encode(text,StandardCharsets.UTF_8);}
    long sku(String code,String name,int onHand,int reserved,boolean active){
        jdbc.update("INSERT INTO sku(code,name,price,active) VALUES(?,?,2.50,?)",code,name,active);
        long id=jdbc.queryForObject("SELECT id FROM sku WHERE code=?",Long.class,code);
        jdbc.update("INSERT INTO inventory(sku_id,on_hand,reserved) VALUES(?,?,?)",id,onHand,reserved);return id;
    }
    long order(long buyer,String status,String total){
        String key=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer_order(buyer_id,request_key,payload_hash,status,total,expires_at) VALUES(?,?,'test',?,?,DATE_ADD(NOW(),INTERVAL 1 DAY))",buyer,key,status,new BigDecimal(total));
        return jdbc.queryForObject("SELECT id FROM customer_order WHERE buyer_id=? AND request_key=?",Long.class,buyer,key);
    }
    void ledger(long sku){jdbc.update("INSERT INTO inventory_ledger(sku_id,actor_id,operation_type,operation_key,quantity) VALUES(?,3,'INBOUND',?,1)",sku,UUID.randomUUID().toString());}
    String update(String name,String price,boolean active){return "{\"name\":\""+name+"\",\"price\":"+price+",\"active\":"+active+"}";}
    String purchase(String key,long sku){return "{\"requestKey\":\""+key+"\",\"items\":[{\"skuId\":"+sku+",\"quantity\":2}]}";}


    JsonNode policy(long id,int value)throws Exception{
        var r=request("warehouse","PUT","/api/inventory/"+id+"/policy","{\"safetyStock\":"+value+"}");
        assertEquals(200,r.status(),r.body().toString());return r.body();
    }
    long createOrder(long sku,String key)throws Exception{
        var r=request("buyer","POST","/api/orders",purchase(key,sku));assertEquals(201,r.status(),r.body().toString());return r.body().path("id").asLong();
    }
    JsonNode command(String user,long id,String action)throws Exception{
        var r=request(user,"POST",(user.equals("warehouse")?"/api/fulfillment/orders/":"/api/orders/")+id+"/"+action,null);
        assertEquals(200,r.status(),r.body().toString());return r.body();
    }
    @Test void safetyStockPersistsAndDoesNotChangeStockOrCreateLedger()throws Exception{
        long id=sku("POLICY","Policy",15,3,true);
        assertEquals(10,ok("warehouse","/api/inventory/"+id).path("safetyStock").asInt());
        var updated=policy(id,20);assertEquals(20,updated.path("safetyStock").asInt());assertEquals(12,updated.path("available").asInt());
        assertEquals(20,ok("warehouse","/api/inventory/"+id).path("safetyStock").asInt());
        assertEquals(20,jdbc.queryForObject("SELECT safety_stock FROM inventory WHERE sku_id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class));
        assertEquals(20,policy(id,20).path("safetyStock").asInt());
    }
    @Test void safetyPolicyValidatesInputAndProtectsWarehouseRole()throws Exception{
        long id=sku("VALID-POLICY","Policy",5,0,true);policy(id,10);
        String endpoint="/api/inventory/"+id+"/policy";
        for(String body:List.of("{}","{\"safetyStock\":null}","{\"safetyStock\":0}","{\"safetyStock\":-1}","{\"safetyStock\":1000001}","{\"safetyStock\":1.5}","{\"safetyStock\":10,\"onHand\":99}"))
            assertEquals(400,request("warehouse","PUT",endpoint,body).status(),body);
        assertEquals(403,request("buyer","PUT",endpoint,"{\"safetyStock\":20}").status());
        assertEquals(403,request(null,"PUT",endpoint,"{\"safetyStock\":20}").status());
        assertEquals(200,request("admin","PUT",endpoint,"{\"safetyStock\":1}").status());
        assertEquals(404,request("warehouse","PUT","/api/inventory/999999999/policy","{\"safetyStock\":10}").status());
        assertEquals(400,request("warehouse","PUT","/api/inventory/0/policy","{\"safetyStock\":10}").status());
    }
    @Test void savedPolicyUnifiesAlertsDashboardAndStockFiltersAcrossAllRows()throws Exception{
        long old=sku("LOW-POLICY","Policy low",25,5,true);policy(old,30);
        long equal=sku("EQUAL-POLICY","Policy equal",3,0,true);policy(equal,3);
        long inactive=sku("OFF-POLICY","Policy off",0,0,false);policy(inactive,30);
        for(int n=0;n<105;n++)sku("HEALTH-POLICY-"+n,"Healthy",20,0,true);
        var alerts=ok("warehouse","/api/inventory/alerts");assertEquals(1,alerts.path("total").asInt());assertTrue(alerts.path("threshold").isNull());
        var row=alerts.path("items").get(0);assertEquals(old,row.path("skuId").asLong());assertEquals(30,row.path("safetyStock").asInt());assertEquals(30,row.path("threshold").asInt());assertEquals(10,row.path("shortage").asInt());
        assertEquals(1,ok("warehouse","/api/dashboard").path("lowStockCount").asInt());
        var low=ok("warehouse","/api/inventory/page?status=low");assertEquals(1,low.path("total").asInt());assertEquals(old,low.path("items").get(0).path("skuId").asLong());
        assertEquals(106,ok("warehouse","/api/inventory/page?status=healthy").path("total").asInt());
        assertEquals(1,ok("warehouse","/api/inventory/page?status=empty").path("total").asInt());
        policy(old,20);assertEquals(0,ok("warehouse","/api/inventory/alerts").path("total").asInt());assertEquals(0,ok("warehouse","/api/dashboard").path("lowStockCount").asInt());
    }
    @Test void optionalQueryThresholdRemainsAnOverrideWithoutChangingSavedPolicy()throws Exception{
        long id=sku("OVERRIDE","Override",12,0,true);policy(id,20);
        assertEquals(1,ok("warehouse","/api/inventory/alerts").path("total").asInt());
        assertEquals(0,ok("warehouse","/api/inventory/alerts?threshold=12").path("total").asInt());
        var override=ok("warehouse","/api/inventory/alerts?threshold=15");assertEquals(15,override.path("threshold").asInt());
        var row=override.path("items").get(0);assertEquals(20,row.path("safetyStock").asInt());assertEquals(15,row.path("threshold").asInt());assertEquals(3,row.path("shortage").asInt());
        assertEquals(20,ok("warehouse","/api/inventory/"+id).path("safetyStock").asInt());
    }
    @Test void lifecycleTimesAreRecordedOnceAndVisibleOnlyToAuthorizedReaders()throws Exception{
        long id=createOrder(sku("TIMELINE","Timeline",20,0,true),"timeline");
        var created=ok("buyer","/api/orders/"+id);assertFalse(created.path("createdAt").asText().isBlank());assertFalse(created.path("createdAt").isMissingNode());
        for(String field:List.of("confirmedAt","shippedAt","completedAt","cancelledAt"))assertTrue(created.path(field).isNull(),field);
        var confirmed=command("buyer",id,"confirm");assertFalse(confirmed.path("confirmedAt").isNull());assertFalse(confirmed.path("confirmedAt").isMissingNode());
        assertEquals(confirmed.path("confirmedAt"),command("buyer",id,"confirm").path("confirmedAt"));
        var shipped=command("warehouse",id,"ship");assertFalse(shipped.path("shippedAt").isNull());assertFalse(shipped.path("shippedAt").isMissingNode());assertEquals(shipped.path("shippedAt"),command("warehouse",id,"ship").path("shippedAt"));
        var done=command("buyer",id,"complete");assertFalse(done.path("completedAt").isNull());assertFalse(done.path("completedAt").isMissingNode());assertEquals(done.path("completedAt"),command("buyer",id,"complete").path("completedAt"));
        assertTrue(done.path("cancelledAt").isNull());
        var sequence=List.of("createdAt","confirmedAt","shippedAt","completedAt");
        for(int n=1;n<sequence.size();n++)assertFalse(java.time.LocalDateTime.parse(done.path(sequence.get(n)).asText()).isBefore(java.time.LocalDateTime.parse(done.path(sequence.get(n-1)).asText())));
        assertEquals(done,ok("warehouse","/api/fulfillment/orders/"+id));assertEquals(404,request("buyer2","GET","/api/orders/"+id,null).status());assertEquals(403,request("buyer","GET","/api/fulfillment/orders/"+id,null).status());
    }
    @Test void cancellationAndExpiryRecordRealTimesAndReplaysKeepOriginal()throws Exception{
        long sku=sku("CANCEL-TIME","Cancel",20,0,true),id=createOrder(sku,"cancel-time");
        var cancelled=command("buyer",id,"cancel");assertFalse(cancelled.path("cancelledAt").isNull());assertFalse(cancelled.path("cancelledAt").isMissingNode());assertEquals("USER",cancelled.path("cancelReason").asText());
        assertEquals(cancelled.path("cancelledAt"),command("buyer",id,"cancel").path("cancelledAt"));assertTrue(cancelled.path("confirmedAt").isNull());
        long expired=createOrder(sku,"expiry-time");jdbc.update("UPDATE customer_order SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?",expired);
        orderService.expire(expired);var detail=ok("buyer","/api/orders/"+expired);assertEquals("EXPIRED",detail.path("cancelReason").asText());assertFalse(detail.path("cancelledAt").isNull());
        assertEquals(0,jdbc.queryForObject("SELECT reserved FROM inventory WHERE sku_id=?",Long.class,sku));
    }
    @Test void failedTransitionDoesNotWriteTimeAndLegacyTimesStayUnknown()throws Exception{
        long sku=sku("FAILED-TIME","Failed",20,0,true),id=createOrder(sku,"failed-time");command("buyer",id,"confirm");
        jdbc.update("UPDATE inventory SET reserved=0 WHERE sku_id=?",sku);
        assertEquals(409,request("warehouse","POST","/api/fulfillment/orders/"+id+"/ship",null).status());
        var detail=ok("buyer","/api/orders/"+id);assertTrue(detail.path("shippedAt").isNull());assertEquals("CONFIRMED",detail.path("status").asText());
        long legacy=order(1,"COMPLETED","9.00");var old=ok("buyer","/api/orders/"+legacy);
        assertFalse(old.path("createdAt").isMissingNode());for(String field:List.of("confirmedAt","shippedAt","completedAt","cancelledAt"))assertTrue(old.path(field).isNull(),field);
    }
}
