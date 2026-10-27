package io.github.stockflow;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DocumentIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final JsonMapper json=JsonMapper.builder().build();
    record Reply(int status,JsonNode body){}
    @BeforeEach void resetOnlyTestSchema(){
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        for(String table:List.of("operation_audit","inventory_ledger","return_item","customer_return","receipt_item","receipt_document","stocktake","order_item","customer_order","inventory","sku"))
            if(scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",table)>0)jdbc.update("DELETE FROM "+table);
    }
    long scalar(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    Reply request(String user,String method,String path,String payload)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8)));
        if(payload!=null)builder.header("Content-Type","application/json");
        var r=client.send(builder.method(method,payload==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(),r.body().isBlank()?json.nullNode():json.readTree(r.body()));
    }
    JsonNode ok(String user,String path)throws Exception{var r=request(user,"GET",path,null);assertEquals(200,r.status(),r.body().toString());return r.body();}
    long sku(String code,int onHand)throws Exception{
        var r=request("warehouse","POST","/api/skus","{\"code\":\""+code+"\",\"name\":\"原始商品\",\"price\":2.50}");assertEquals(201,r.status(),r.body().toString());long id=r.body().path("id").asLong();
        if(onHand>0)assertEquals(200,request("warehouse","POST","/api/inventory/"+id+"/receipts","{\"operationKey\":\""+UUID.randomUUID()+"\",\"quantity\":"+onHand+"}").status());return id;
    }
    String receipt(String key,String source,long... skus){var items=new ArrayList<Map<String,Object>>();for(long sku:skus)items.add(Map.of("skuId",sku,"quantity",3));return json.writeValueAsString(Map.of("requestKey",key,"source",source,"items",items));}
    long draft(long...skus)throws Exception{var r=request("warehouse","POST","/api/receipts",receipt(UUID.randomUUID().toString(),"供应商到货",skus));assertEquals(201,r.status(),r.body().toString());return r.body().path("id").asLong();}
    Reply receiptCommand(long id,String action)throws Exception{return request("warehouse","POST","/api/receipts/"+id+"/"+action,null);}
    long completed(long sku)throws Exception{
        var r=request("buyer","POST","/api/orders",json.writeValueAsString(Map.of("requestKey",UUID.randomUUID().toString(),"items",List.of(Map.of("skuId",sku,"quantity",2)))));assertEquals(201,r.status(),r.body().toString());long id=r.body().path("id").asLong();
        assertEquals(200,request("buyer","POST","/api/orders/"+id+"/confirm",null).status());assertEquals(200,request("warehouse","POST","/api/fulfillment/orders/"+id+"/ship",null).status());assertEquals(200,request("buyer","POST","/api/orders/"+id+"/complete",null).status());return id;
    }
    String returnPayload(String key,long order,String reason){return json.writeValueAsString(Map.of("requestKey",key,"orderId",order,"reason",reason));}
    Reply createReturn(String user,String key,long order,String reason)throws Exception{return request(user,"POST","/api/returns",returnPayload(key,order,reason));}
    long returnDraft(long order)throws Exception{var r=createReturn("buyer",UUID.randomUUID().toString(),order,"商品退回");assertEquals(201,r.status(),r.body().toString());return r.body().path("id").asLong();}
    @Test void receiptIsImmutableIdempotentAndConfirmChangesStockLedgerAuditExactlyOnce()throws Exception{
        long a=sku("DOC-A",0),b=sku("DOC-B",0);String key=UUID.randomUUID().toString(),payload=receipt(key,"  供应商到货  ",b,a);
        var first=request("warehouse","POST","/api/receipts",payload);assertEquals(201,first.status(),first.body().toString());long id=first.body().path("id").asLong();assertEquals("DRAFT",first.body().path("status").asText());assertEquals("供应商到货",first.body().path("source").asText());
        assertEquals(200,request("warehouse","POST","/api/receipts",receipt(key,"供应商到货",a,b)).status());assertEquals(409,request("warehouse","POST","/api/receipts",receipt(key,"其他",a,b)).status());assertEquals(0,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));
        jdbc.update("UPDATE sku SET name='当前新名称' WHERE id=?",a);assertEquals("原始商品",ok("warehouse","/api/receipts/"+id).path("items").get(0).path("skuName").asText());
        assertEquals(200,receiptCommand(id,"confirm").status());assertEquals(200,receiptCommand(id,"confirm").status());assertEquals(409,receiptCommand(id,"cancel").status());
        assertEquals(3,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));assertEquals(1,scalar("SELECT version FROM inventory WHERE sku_id=?",a));assertEquals(2,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE receipt_id=?",id));assertEquals(1,ok("warehouse","/api/audit?action=RECEIPT_CONFIRM").path("total").asInt());assertEquals(0,ok("warehouse","/api/inventory/reconciliation").path("mismatchCount").asInt());
        assertEquals(id,ok("warehouse","/api/inventory/ledger?type=INBOUND").path("items").get(0).path("receiptId").asLong());
        assertEquals(1,ok("warehouse","/api/receipts?status=CONFIRMED&page=99").path("total").asInt());assertEquals(0,ok("warehouse","/api/receipts?q=missing").path("total").asInt());assertEquals(400,request("warehouse","GET","/api/receipts?status=BAD",null).status());
    }
    @Test void receiptValidationCancellationAndRoles()throws Exception{
        long a=sku("DOC-VALID",0);assertEquals(400,request("warehouse","POST","/api/receipts",receipt("empty"," ",a)).status());assertEquals(400,request("warehouse","POST","/api/receipts",receipt("dupes","x",a,a)).status());
        assertEquals(400,request("warehouse","POST","/api/receipts","{\"requestKey\":\"bad\",\"source\":\"x\",\"items\":[{\"skuId\":"+a+",\"quantity\":0}]}").status());assertEquals(403,request("buyer","POST","/api/receipts",receipt("buyer","x",a)).status());assertEquals(403,request("buyer","GET","/api/receipts",null).status());
        long id=draft(a);assertEquals(200,receiptCommand(id,"cancel").status());assertEquals(200,receiptCommand(id,"cancel").status());assertEquals(409,receiptCommand(id,"confirm").status());assertEquals(0,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE receipt_id=?",id));assertEquals(1,ok("warehouse","/api/audit?action=RECEIPT_CANCEL").path("total").asInt());assertEquals(200,request("admin","GET","/api/receipts/"+id,null).status());
    }
    @Test void concurrentReceiptConfirmationAndLateLineFailureRemainAtomic()throws Exception{
        long a=sku("DOC-ATOMIC-A",0),b=sku("DOC-ATOMIC-B",0),id=draft(b,a);jdbc.update("UPDATE inventory SET on_hand=1000000000 WHERE sku_id=?",b);
        assertEquals(409,receiptCommand(id,"confirm").status());assertEquals(0,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));assertEquals(0,scalar("SELECT version FROM inventory WHERE sku_id=?",a));assertEquals("DRAFT",ok("warehouse","/api/receipts/"+id).path("status").asText());assertEquals(0,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE receipt_id=?",id));
        jdbc.update("UPDATE inventory SET on_hand=0 WHERE sku_id=?",b);var pool=Executors.newFixedThreadPool(2);try{var x=pool.submit(()->receiptCommand(id,"confirm"));var y=pool.submit(()->receiptCommand(id,"confirm"));assertEquals(200,x.get().status());assertEquals(200,y.get().status());}finally{pool.shutdownNow();}assertEquals(3,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));assertEquals(2,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE receipt_id=?",id));
    }
    @Test void returnUsesWholeOriginalOrderAndDoesNotReopenIt()throws Exception{
        long a=sku("DOC-RETURN",10),order=completed(a);jdbc.update("UPDATE sku SET name='新名称',active=FALSE WHERE id=?",a);String key=UUID.randomUUID().toString();var r=createReturn("buyer",key,order,"  商品退回  ");assertEquals(201,r.status(),r.body().toString());long id=r.body().path("id").asLong();
        assertEquals(200,createReturn("buyer",key,order,"商品退回").status());assertEquals(409,createReturn("buyer",key,order,"不同原因").status());assertEquals(409,createReturn("buyer",UUID.randomUUID().toString(),order,"商品退回").status());assertEquals(2,r.body().path("items").get(0).path("quantity").asInt());assertEquals("原始商品",r.body().path("items").get(0).path("skuName").asText());
        assertEquals(403,request("buyer","POST","/api/returns/"+id+"/confirm",null).status());assertEquals(200,request("warehouse","POST","/api/returns/"+id+"/confirm",null).status());assertEquals(200,request("warehouse","POST","/api/returns/"+id+"/confirm",null).status());assertEquals(409,request("buyer","POST","/api/returns/"+id+"/cancel",null).status());
        assertEquals(10,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));assertEquals("COMPLETED",ok("buyer","/api/orders/"+order).path("status").asText());assertEquals(1,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE return_id=?",id));assertEquals(id,ok("warehouse","/api/inventory/ledger?type=RETURN_IN").path("items").get(0).path("returnId").asLong());assertEquals(0,ok("warehouse","/api/inventory/reconciliation").path("mismatchCount").asInt());assertEquals(1,ok("warehouse","/api/audit?action=RETURN_CONFIRM").path("total").asInt());
    }
    @Test void returnsEnforceOwnershipCancellationAndOneActivePerOrder()throws Exception{
        long a=sku("DOC-OWNER",10),order=completed(a);assertEquals(404,createReturn("buyer2",UUID.randomUUID().toString(),order,"其他买家").status());assertEquals(403,createReturn("warehouse",UUID.randomUUID().toString(),order,"仓库").status());long id=returnDraft(order);
        assertEquals(404,request("buyer2","GET","/api/returns/"+id,null).status());assertEquals(404,request("buyer2","POST","/api/returns/"+id+"/cancel",null).status());assertEquals(0,ok("buyer2","/api/returns").path("total").asInt());assertEquals(1,ok("warehouse","/api/returns?status=REQUESTED").path("total").asInt());assertEquals(403,request("warehouse","POST","/api/returns/"+id+"/cancel",null).status());
        assertEquals(200,request("buyer","POST","/api/returns/"+id+"/cancel",null).status());assertEquals(200,request("buyer","POST","/api/returns/"+id+"/cancel",null).status());assertEquals(409,request("warehouse","POST","/api/returns/"+id+"/confirm",null).status());long next=returnDraft(order);assertNotEquals(id,next);assertEquals(1,ok("warehouse","/api/audit?action=RETURN_CANCEL").path("total").asInt());
    }
    @Test void competingReturnRequestsAndConfirmationsNeverDuplicateStock()throws Exception{
        long a=sku("DOC-CONCURRENT",10),order=completed(a);var pool=Executors.newFixedThreadPool(2);long id;
        try{var x=pool.submit(()->createReturn("buyer",UUID.randomUUID().toString(),order,"退回"));var y=pool.submit(()->createReturn("buyer",UUID.randomUUID().toString(),order,"退回"));var xr=x.get();var yr=y.get();assertEquals(List.of(201,409),java.util.stream.Stream.of(xr.status(),yr.status()).sorted().toList());id=(xr.status()==201?xr:yr).body().path("id").asLong();long returnId=id;var c=pool.submit(()->request("warehouse","POST","/api/returns/"+returnId+"/confirm",null));var d=pool.submit(()->request("warehouse","POST","/api/returns/"+returnId+"/confirm",null));assertEquals(200,c.get().status());assertEquals(200,d.get().status());}finally{pool.shutdownNow();}
        assertEquals(10,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));assertEquals(1,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE return_id=?",id));assertEquals(409,createReturn("buyer",UUID.randomUUID().toString(),order,"再次退回").status());
    }
    @Test void returnRequiresCompletedOrderAndValidReason()throws Exception{
        long a=sku("DOC-STATE",10);var r=request("buyer","POST","/api/orders",json.writeValueAsString(Map.of("requestKey","pending","items",List.of(Map.of("skuId",a,"quantity",1)))));assertEquals(201,r.status());long id=r.body().path("id").asLong();assertEquals(409,createReturn("buyer","return-pending",id,"退回").status());assertEquals(400,createReturn("buyer","blank",id," ").status());assertEquals(400,createReturn("buyer","long",id,"x".repeat(241)).status());assertEquals(400,request("buyer","GET","/api/returns?status=BAD",null).status());
    }
    @Test void receiptLedgerConflictRollsBackAllLinesAndAudit()throws Exception{
        long a=sku("DOC-LEDGER-A",0),b=sku("DOC-LEDGER-B",0),id=draft(a,b);
        jdbc.update("INSERT INTO inventory_ledger(sku_id,receipt_id,actor_id,operation_type,operation_key,quantity) VALUES(?,?,3,'INBOUND',?,3)",b,id,"receipt:"+id);
        assertEquals(409,receiptCommand(id,"confirm").status());assertEquals(0,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));assertEquals(0,scalar("SELECT version FROM inventory WHERE sku_id=?",a));assertEquals(0,scalar("SELECT COUNT(*) FROM operation_audit WHERE action='RECEIPT_CONFIRM'"));assertEquals("DRAFT",ok("warehouse","/api/receipts/"+id).path("status").asText());
    }
    @Test void legacyReturnKeepsUnknownNamesAndAllLinesRollbackAtStockLimit()throws Exception{
        long a=sku("DOC-LEGACY-A",10),b=sku("DOC-LEGACY-B",10);String key=UUID.randomUUID().toString();jdbc.update("INSERT INTO customer_order(buyer_id,request_key,payload_hash,status,total,expires_at) VALUES(1,?,'legacy','COMPLETED',10,DATE_ADD(NOW(),INTERVAL 1 DAY))",key);long order=scalar("SELECT id FROM customer_order WHERE request_key=?",key);
        for(long sku:List.of(a,b))jdbc.update("INSERT INTO order_item(order_id,sku_id,quantity,unit_price) VALUES(?,?,2,2.50)",order,sku);
        long id=returnDraft(order);var item=ok("buyer","/api/returns/"+id).path("items").get(0);assertTrue(item.path("skuCode").isNull());assertTrue(item.path("skuName").isNull());long version=scalar("SELECT version FROM inventory WHERE sku_id=?",a);jdbc.update("UPDATE inventory SET on_hand=1000000000 WHERE sku_id=?",b);
        assertEquals(409,request("warehouse","POST","/api/returns/"+id+"/confirm",null).status());assertEquals(10,scalar("SELECT on_hand FROM inventory WHERE sku_id=?",a));assertEquals(version,scalar("SELECT version FROM inventory WHERE sku_id=?",a));assertEquals("REQUESTED",ok("buyer","/api/returns/"+id).path("status").asText());assertEquals(0,scalar("SELECT COUNT(*) FROM inventory_ledger WHERE return_id=?",id));assertEquals(0,scalar("SELECT COUNT(*) FROM operation_audit WHERE action='RETURN_CONFIRM'"));
    }
}
