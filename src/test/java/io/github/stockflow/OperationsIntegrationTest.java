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
class OperationsIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
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

    long seeded(int quantity)throws Exception{
        var r=request("warehouse","POST","/api/skus","{\"code\":\"COUNT-"+UUID.randomUUID().toString().substring(0,20).toUpperCase(Locale.ROOT)+"\",\"name\":\"盘点测试商品\",\"price\":3.50}");assertEquals(201,r.status(),r.body().toString());
        long id=r.body().path("id").asLong();receive(id,quantity);return id;
    }
    void receive(long id,int quantity)throws Exception{assertEquals(200,request("warehouse","POST","/api/inventory/"+id+"/receipts","{\"operationKey\":\""+UUID.randomUUID()+"\",\"quantity\":"+quantity+"}").status());}
    Reply draft(long id,int target,String key)throws Exception{return request("warehouse","POST","/api/stocktakes","{\"requestKey\":\""+key+"\",\"skuId\":"+id+",\"targetOnHand\":"+target+",\"reason\":\"  货架实盘  \"}");}
    long draft(long id,int target)throws Exception{var r=draft(id,target,UUID.randomUUID().toString());assertEquals(201,r.status(),r.body().toString());return r.body().path("id").asLong();}
    Reply command(long id,String action)throws Exception{return request("warehouse","POST","/api/stocktakes/"+id+"/"+action,null);}
    long scalar(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    @Test void auditRecordsActorBeforeAfterAndAvoidsNoChangeEvents()throws Exception{
        long id=seeded(10);
        var created=ok("warehouse","/api/audit?action=SKU_CREATE").path("items").get(0);
        assertEquals("warehouse",created.path("username").asText());assertTrue(created.path("before").isNull());assertEquals(id,created.path("entityId").asLong());assertFalse(created.path("requestId").asText().isBlank());
        var old=ok("warehouse","/api/skus/"+id);String payload="{\"name\":\"更名商品\",\"price\":4.25,\"active\":true}";
        assertEquals(200,request("warehouse","PUT","/api/skus/"+id,payload).status());
        assertEquals(200,request("warehouse","PUT","/api/skus/"+id,payload).status());
        var edit=ok("admin","/api/audit?action=SKU_UPDATE");assertEquals(1,edit.path("total").asInt());assertEquals(old.path("name").asText(),edit.path("items").get(0).path("before").path("name").asText());assertEquals("更名商品",edit.path("items").get(0).path("after").path("name").asText());
        assertEquals(200,request("warehouse","PUT","/api/inventory/"+id+"/policy","{\"safetyStock\":30}").status());
        var policy=ok("warehouse","/api/audit?action=STOCK_POLICY").path("items").get(0);assertEquals(10,policy.path("before").path("safetyStock").asInt());assertEquals(30,policy.path("after").path("safetyStock").asInt());
        assertEquals(403,request("buyer","GET","/api/audit",null).status());assertEquals(400,request("warehouse","GET","/api/audit?action=BAD",null).status());
    }
    @Test void stocktakeAdjustsOnceAndConnectsAuditLedgerAndReconciliation()throws Exception{
        long sku=seeded(10);String key=UUID.randomUUID().toString();var first=draft(sku,14,key);assertEquals(201,first.status(),first.body().toString());long id=first.body().path("id").asLong();
        assertEquals(200,draft(sku,14,key).status());assertEquals(409,draft(sku,13,key).status());
        var confirmed=command(id,"confirm");assertEquals(200,confirmed.status(),confirmed.body().toString());assertEquals(4,confirmed.body().path("adjustment").asInt());assertFalse(confirmed.body().path("confirmedAt").isNull());
        assertEquals(200,command(id,"confirm").status());assertEquals(409,command(id,"cancel").status());
        assertEquals(14,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",sku));assertEquals(1,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE stocktake_id=?",id));
        var entry=ok("warehouse","/api/inventory/ledger?type=ADJUST_IN").path("items").get(0);assertEquals(id,entry.path("stocktakeId").asLong());assertEquals(4,entry.path("onHandDelta").asInt());
        assertEquals(1,ok("warehouse","/api/audit?action=STOCKTAKE_CONFIRM").path("total").asInt());
        var recon=ok("warehouse","/api/inventory/reconciliation");assertEquals(0,recon.path("mismatchCount").asInt());assertEquals(1,recon.path("checkedCount").asInt());
        long down=draft(sku,8);assertEquals(200,command(down,"confirm").status());assertEquals(-6,ok("warehouse","/api/inventory/ledger?type=ADJUST_OUT").path("items").get(0).path("onHandDelta").asInt());
        long zero=draft(sku,8);assertEquals(200,command(zero,"confirm").status());assertEquals(0,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE stocktake_id=?",zero));assertEquals(0,ok("warehouse","/api/inventory/reconciliation").path("total").asInt());
    }
    @Test void stocktakeRejectsStaleSnapshotEvenWhenQuantitiesReturnToOriginal()throws Exception{
        long sku=seeded(10),id=draft(sku,9);
        var purchase=request("buyer","POST","/api/orders","{\"requestKey\":\""+UUID.randomUUID()+"\",\"items\":[{\"skuId\":"+sku+",\"quantity\":2}]}");assertEquals(201,purchase.status(),purchase.body().toString());
        assertEquals(200,request("buyer","POST","/api/orders/"+purchase.body().path("id").asLong()+"/cancel",null).status());
        assertEquals(10,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",sku));assertEquals(0,scalar("SELECT reserved FROM inventory WHERE sku_id=?",sku));
        var result=command(id,"confirm");assertEquals(409,result.status());assertEquals("STOCK_CHANGED",result.body().path("code").asText());assertEquals("DRAFT",ok("warehouse","/api/stocktakes/"+id).path("status").asText());
        assertEquals(200,command(id,"cancel").status());assertEquals(200,command(id,"cancel").status());assertEquals(409,command(id,"confirm").status());
        long fresh=draft(sku,12);receive(sku,1);assertEquals(409,command(fresh,"confirm").status());
    }
    @Test void cannotCountBelowReservedAndBuyersCannotAccessOperations()throws Exception{
        long sku=seeded(10);var p=request("buyer","POST","/api/orders","{\"requestKey\":\""+UUID.randomUUID()+"\",\"items\":[{\"skuId\":"+sku+",\"quantity\":4}]}");assertEquals(201,p.status());long id=draft(sku,3);
        var r=command(id,"confirm");assertEquals(409,r.status());assertEquals("RESERVED_STOCK",r.body().path("code").asText());
        for(String path:List.of("/api/stocktakes","/api/stocktakes/"+id,"/api/inventory/reconciliation"))assertEquals(403,request("buyer","GET",path,null).status());
        assertEquals(403,request("buyer","POST","/api/stocktakes/"+id+"/confirm",null).status());
        assertEquals(400,draft(sku,-1,UUID.randomUUID().toString()).status());assertEquals(400,request("warehouse","GET","/api/stocktakes?status=BAD",null).status());
    }
    @Test void duplicateConfirmationAndCompetingDraftsNeverAdjustTwice()throws Exception{
        long sku=seeded(10),first=draft(sku,15),other=draft(sku,20);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->command(first,"confirm"));var b=pool.submit(()->command(first,"confirm"));assertEquals(200,a.get().status());assertEquals(200,b.get().status());}finally{pool.shutdownNow();}
        assertEquals(15,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",sku));assertEquals(1,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE stocktake_id=?",first));assertEquals(409,command(other,"confirm").status());
    }
    @Test void ledgerFailureRollsBackStockVersionStatusAndAudit()throws Exception{
        long sku=seeded(10),id=draft(sku,15);long version=scalar("SELECT version FROM inventory WHERE sku_id=?",sku);
        jdbc.update("INSERT INTO inventory_ledger(sku_id,stocktake_id,actor_id,operation_type,operation_key,quantity) VALUES(?,?,3,'ADJUST_IN',?,1)",sku,id,"stocktake:"+id);
        assertEquals(409,command(id,"confirm").status());assertEquals(10,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",sku));assertEquals(version,scalar("SELECT version FROM inventory WHERE sku_id=?",sku));assertEquals("DRAFT",ok("warehouse","/api/stocktakes/"+id).path("status").asText());assertEquals(0,scalar("SELECT COUNT(*) FROM operation_audit WHERE action='STOCKTAKE_CONFIRM'"));
    }
    @Test void reconciliationDetectsCorruptionWithoutMutatingInventory()throws Exception{
        long sku=seeded(10);assertEquals(0,ok("warehouse","/api/inventory/reconciliation").path("total").asInt());jdbc.update("UPDATE inventory SET on_hand=11 WHERE sku_id=?",sku);
        var r=ok("warehouse","/api/inventory/reconciliation");assertEquals(1,r.path("mismatchCount").asInt());var row=r.path("items").get(0);assertEquals(11,row.path("onHand").asInt());assertEquals(10,row.path("ledgerOnHand").asInt());assertFalse(row.path("consistent").asBoolean());assertEquals(11,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",sku));
        assertEquals(1,ok("warehouse","/api/inventory/reconciliation?mismatchOnly=false&page=99").path("total").asInt());assertEquals(0,ok("warehouse","/api/inventory/reconciliation?q=missing").path("checkedCount").asInt());
    }
    @Test void trendsUseSevenRealDatesAndBuyerScope()throws Exception{
        long sku=seeded(10);order(1,"SHIPPED","2");order(2,"SHIPPED","8");
        var warehouse=ok("warehouse","/api/dashboard/trends");var own=ok("buyer","/api/dashboard/trends");assertEquals(7,warehouse.size());assertEquals(2,warehouse.get(6).path("orders").asInt());assertEquals(10,warehouse.get(6).path("inbound").asInt());assertEquals(1,own.get(6).path("orders").asInt());assertEquals(0,own.get(6).path("inbound").asInt());assertEquals(0,own.get(6).path("outbound").asInt());assertEquals(0,warehouse.get(0).path("orders").asInt());
    }
    @Test void wrongMethodAndMediaTypeHaveAccurateHttpStatus()throws Exception{
        assertEquals(405,request("warehouse","DELETE","/api/stocktakes",null).status());
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/stocktakes")).header("Authorization","Basic "+Base64.getEncoder().encodeToString(("warehouse:"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8))).header("Content-Type","text/plain").POST(HttpRequest.BodyPublishers.ofString("bad"));
        assertEquals(415,client.send(b.build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void concurrentReservationProducesMeasuredEvidenceWithoutOverselling()throws Exception{
        long sku=seeded(20);var pool=java.util.concurrent.Executors.newFixedThreadPool(16);var durations=Collections.synchronizedList(new ArrayList<Long>());var statuses=new java.util.concurrent.ConcurrentHashMap<Integer,java.util.concurrent.atomic.AtomicInteger>();
        long started=System.nanoTime();
        try{var jobs=new ArrayList<java.util.concurrent.Callable<Void>>();for(int n=0;n<100;n++)jobs.add(()->{long begin=System.nanoTime();var r=request("buyer","POST","/api/orders","{\"requestKey\":\""+UUID.randomUUID()+"\",\"items\":[{\"skuId\":"+sku+",\"quantity\":1}]}");durations.add((System.nanoTime()-begin)/1000000);statuses.computeIfAbsent(r.status(),unused->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();return null;});for(var future:pool.invokeAll(jobs))future.get();}finally{pool.shutdownNow();}
        double seconds=(System.nanoTime()-started)/1e9;Collections.sort(durations);assertEquals(20,statuses.get(201).get());assertEquals(80,statuses.get(409).get());assertEquals(2,statuses.size());assertEquals(20,scalar("SELECT reserved FROM inventory WHERE sku_id=?",sku));assertEquals(0,ok("warehouse","/api/inventory/reconciliation").path("mismatchCount").asInt());
        var evidence=new LinkedHashMap<String,Object>();evidence.put("scenario","100 real HTTP order requests; 16 clients; one SKU with 20 units; Basic authentication included");evidence.put("environment",System.getProperty("os.name")+"; Java "+System.getProperty("java.version")+"; CPUs "+Runtime.getRuntime().availableProcessors());evidence.put("requests",100);evidence.put("clients",16);evidence.put("seconds",seconds);evidence.put("requestsPerSecond",100/seconds);evidence.put("p50Milliseconds",durations.get(49));evidence.put("p95Milliseconds",durations.get(94));evidence.put("created",20);evidence.put("stockConflicts",80);evidence.put("unexpectedResponses",0);evidence.put("reserved",20);evidence.put("reconciliationMismatches",0);
        evidence.put("ledgerPlan",jdbc.queryForList("EXPLAIN SELECT id FROM inventory_ledger WHERE sku_id=? ORDER BY id DESC LIMIT 20",sku));evidence.put("expirationPlan",jdbc.queryForList("EXPLAIN SELECT id FROM customer_order WHERE status='PENDING_CONFIRM' AND expires_at<CURRENT_TIMESTAMP LIMIT 100"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/performance-evidence.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
}
