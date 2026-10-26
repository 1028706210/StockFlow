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
class WorkbenchIntegrationTest {
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

    @Test void catalogAndInventorySearchAllRowsAndClampPagesWithExactTotals()throws Exception{
        long oldest=sku("OLD_MATCH","Older result",3,1,true);
        for(int n=0;n<105;n++)sku("NEW-"+n,"Newer product",20,0,true);
        for(String path:List.of("/api/skus/page","/api/inventory/page")){
            var all=ok("warehouse",path+"?size=8&page=1000000");
            assertEquals(106,all.path("total").asInt());assertEquals(14,all.path("totalPages").asInt());assertEquals(14,all.path("page").asInt());assertEquals(8,all.path("size").asInt());assertEquals(2,all.path("items").size());
            var result=ok("warehouse",path+"?q=OLD_MATCH&page=9");
            assertEquals(1,result.path("total").asInt());assertEquals(1,result.path("page").asInt());assertEquals(1,result.path("totalPages").asInt());
            var row=result.path("items").get(0);assertEquals(oldest,row.path(path.contains("inventory")?"skuId":"id").asLong());
            if(path.contains("inventory")){assertEquals("Older result",row.path("name").asText());assertEquals(2,row.path("available").asInt());assertEquals(0,new BigDecimal("2.50").compareTo(row.path("price").decimalValue()));assertTrue(row.path("active").asBoolean());}
            var none=ok("warehouse",path+"?q=missing&page=99");assertEquals(0,none.path("total").asInt());assertEquals(1,none.path("page").asInt());assertEquals(1,none.path("totalPages").asInt());assertEquals(0,none.path("items").size());
        }
        assertEquals(100,ok("warehouse","/api/skus").size());assertEquals(100,ok("warehouse","/api/inventory").size());
    }
    @Test void productQueriesHandleUnicodeAndTreatWildcardsLiterally()throws Exception{
        long literal=sku("MATCH_A","海盐 100% ! 陶瓷杯 📦",1,0,true);
        sku("MATCHXA","100 percent cup",1,0,true);
        for(String path:List.of("/api/skus/page","/api/inventory/page")){
            for(String term:List.of("陶瓷","📦","%","_","!")){
                var result=ok("warehouse",path+"?q="+q(term));assertEquals(1,result.path("total").asInt(),term);
                assertEquals(literal,result.path("items").get(0).path(path.contains("inventory")?"skuId":"id").asLong());
            }
            assertEquals(0,ok("warehouse",path+"?q="+q("不存在的商品")).path("total").asInt());
            assertEquals(0,ok("warehouse",path+"?q="+q("' OR 1=1 --")).path("total").asInt());
        }
    }
    @Test void productAndStockStatusFiltersMatchTheirBoundaries()throws Exception{
        sku("EMPTY","Empty",4,4,true);sku("LOW","Low",10,1,true);sku("HEALTHY","Healthy",10,0,true);sku("OFF","Inactive",0,0,false);
        assertEquals(4,ok("warehouse","/api/skus/page").path("total").asInt());assertEquals(3,ok("buyer","/api/skus/page?status=active").path("total").asInt());assertEquals(1,ok("warehouse","/api/skus/page?status=inactive").path("total").asInt());
        assertEquals(2,ok("warehouse","/api/inventory/page?status=empty").path("total").asInt());assertEquals(1,ok("warehouse","/api/inventory/page?status=low").path("total").asInt());assertEquals(1,ok("warehouse","/api/inventory/page?status=healthy").path("total").asInt());
    }
    @Test void buyerOrderPagesIncludeOldRowsAndNeverExposeOtherBuyers()throws Exception{
        long oldest=order(1,"CANCELLED","1.00");for(int n=0;n<105;n++)order(1,"PENDING_CONFIRM","2.00");long other=order(2,"CONFIRMED","999.00");
        var all=ok("buyer","/api/orders/page?size=100");assertEquals(106,all.path("total").asInt());assertEquals(100,all.path("items").size());assertEquals(2,all.path("totalPages").asInt());
        var old=ok("buyer","/api/orders/page?q=SF-"+oldest+"&status=CANCELLED");assertEquals(1,old.path("total").asInt());assertEquals(oldest,old.path("items").get(0).path("id").asLong());
        assertEquals(1,ok("buyer","/api/orders/page?q="+oldest+"&status=CANCELLED").path("total").asInt());
        assertEquals(0,ok("buyer","/api/orders/page?q=SF-"+other).path("total").asInt());assertEquals(1,ok("buyer2","/api/orders/page").path("total").asInt());
        var last=ok("buyer","/api/orders/page?size=100&page=999");assertEquals(2,last.path("page").asInt());assertEquals(6,last.path("items").size());assertEquals(oldest,last.path("items").get(5).path("id").asLong());
        assertEquals(100,ok("buyer","/api/orders").size());
    }
    @Test void fulfillmentSearchesBuyerIdsAndEveryOrderStatusAcrossAllRows()throws Exception{
        long first=order(1,"CONFIRMED","2.00");for(int n=0;n<105;n++)order(2,"PENDING_CONFIRM","1.00");
        var all=ok("warehouse","/api/fulfillment/orders/page?size=100&page=99");assertEquals(106,all.path("total").asInt());assertEquals(2,all.path("page").asInt());assertEquals(6,all.path("items").size());
        assertEquals(1,ok("admin","/api/fulfillment/orders/page?q=SF-"+first+"&status=CONFIRMED").path("total").asInt());
        var buyerTwo=ok("warehouse","/api/fulfillment/orders/page?q=2&status=PENDING_CONFIRM");assertEquals(105,buyerTwo.path("total").asInt());
        for(String status:List.of("SHIPPED","COMPLETED","CANCELLED")){order(1,status,"1");assertEquals(1,ok("warehouse","/api/fulfillment/orders/page?status="+status).path("total").asInt());}
        assertEquals(0,ok("warehouse","/api/fulfillment/orders/page?q="+q("📦")).path("total").asInt());assertEquals(0,ok("warehouse","/api/fulfillment/orders/page?q=%25").path("total").asInt());
        assertEquals(100,ok("warehouse","/api/fulfillment/orders").size());
    }
    @Test void dashboardAggregatesWholeDatabaseAndScopesBuyerOrders()throws Exception{
        sku("OLD-LOW","Old low stock",3,1,true);for(int n=0;n<105;n++)sku("FULL-"+n,"Healthy",20,2,true);sku("OFF","Inactive",7,0,false);
        for(int n=0;n<105;n++)order(1,"PENDING_CONFIRM","2.50");order(1,"CANCELLED","3.00");order(2,"CONFIRMED","99.00");
        var warehouse=ok("warehouse","/api/dashboard");assertEquals(107,warehouse.path("productCount").asInt());assertEquals(2110,warehouse.path("onHand").asLong());assertEquals(211,warehouse.path("reserved").asLong());assertEquals(1,warehouse.path("lowStockCount").asLong());assertEquals(107,warehouse.path("orderCount").asLong());assertEquals(105,warehouse.path("pendingCount").asLong());assertEquals(0,new BigDecimal("364.50").compareTo(warehouse.path("orderTotal").decimalValue()));
        var buyer=ok("buyer","/api/dashboard");assertEquals(106,buyer.path("productCount").asLong());assertEquals(106,buyer.path("orderCount").asLong());assertEquals(105,buyer.path("pendingCount").asLong());assertEquals(0,new BigDecimal("265.50").compareTo(buyer.path("orderTotal").decimalValue()));
        assertEquals(0,buyer.path("onHand").asLong());assertEquals(0,buyer.path("reserved").asLong());assertEquals(0,buyer.path("lowStockCount").asLong());
        var second=ok("buyer2","/api/dashboard");assertEquals(1,second.path("orderCount").asLong());assertEquals(0,second.path("pendingCount").asLong());assertEquals(0,new BigDecimal("99.00").compareTo(second.path("orderTotal").decimalValue()));
    }
    @Test void emptyDashboardReturnsZeroRatherThanNull()throws Exception{
        for(String user:List.of("buyer","warehouse","admin")){
            var result=ok(user,"/api/dashboard");for(String key:List.of("productCount","onHand","reserved","lowStockCount","orderCount","pendingCount","orderTotal"))assertEquals(0,result.path(key).decimalValue().compareTo(BigDecimal.ZERO),key);
        }
    }
    @Test void newReadEndpointsEnforceAuthenticationAndRoleBoundaries()throws Exception{
        long id=sku("READ","Read",10,0,true);
        for(String path:List.of("/api/skus/page","/api/inventory/page","/api/orders/page","/api/fulfillment/orders/page","/api/dashboard","/api/skus/"+id,"/api/inventory/"+id))assertEquals(401,request(null,"GET",path,null).status(),path);
        for(String path:List.of("/api/inventory/page","/api/inventory/"+id,"/api/fulfillment/orders/page")){assertEquals(403,request("buyer","GET",path,null).status(),path);assertEquals(200,request("admin","GET",path,null).status(),path);}
        assertEquals(403,request("warehouse","GET","/api/orders/page",null).status());assertEquals(200,request("buyer","GET","/api/skus/"+id,null).status());
    }
    @Test void pageFiltersRejectOutOfRangeAndMalformedInputs()throws Exception{
        for(String path:List.of("/api/skus/page","/api/inventory/page","/api/orders/page","/api/fulfillment/orders/page")){
            String user=path.equals("/api/orders/page")?"buyer":"warehouse";
            for(String query:List.of("page=0","page=1000001","page=oops","size=0","size=101","size=1.5","q="+"a".repeat(121),"status=wrong"))assertEquals(400,request(user,"GET",path+"?"+query,null).status(),path+"?"+query);
        }
    }
    @Test void catalogEditsTrimNamesPreserveCodeAndProtectHistoricalPrices()throws Exception{
        long id=sku("IMMUTABLE","Original",20,0,true);
        var original=request("buyer","POST","/api/orders",purchase("old",id));assertEquals(201,original.status(),original.body().toString());long oid=original.body().path("id").asLong();
        var edit=request("warehouse","PUT","/api/skus/"+id,update("  新商品名称  ","4.25",true));assertEquals(200,edit.status(),edit.body().toString());assertEquals("IMMUTABLE",edit.body().path("code").asText());assertEquals("新商品名称",edit.body().path("name").asText());
        var historical=ok("buyer","/api/orders/"+oid);assertEquals(0,new BigDecimal("5.00").compareTo(historical.path("total").decimalValue()));assertEquals(0,new BigDecimal("2.50").compareTo(historical.path("items").get(0).path("unitPrice").decimalValue()));
        var current=request("buyer","POST","/api/orders",purchase("new",id));assertEquals(201,current.status());assertEquals(0,new BigDecimal("8.50").compareTo(current.body().path("total").decimalValue()));
        assertEquals(200,request("admin","PUT","/api/skus/"+id,update("Off shelf","4.25",false)).status());assertFalse(ok("buyer","/api/skus/"+id).path("active").asBoolean());
        assertEquals(404,request("buyer","POST","/api/orders",purchase("inactive",id)).status());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM customer_order",Integer.class));assertEquals(4,jdbc.queryForObject("SELECT reserved FROM inventory WHERE sku_id=?",Long.class,id));
        assertEquals(200,request("buyer","POST","/api/orders",purchase("old",id)).status());
        assertEquals(200,request("warehouse","PUT","/api/skus/"+id,update("Back","0.00",true)).status());assertEquals(201,request("buyer","POST","/api/orders",purchase("zero-price",id)).status());
    }
    @Test void catalogEditValidationAndRolesCannotChangeOtherFields()throws Exception{
        long id=sku("SAFE","Original",8,0,true);
        assertEquals(403,request("buyer","PUT","/api/skus/"+id,update("Changed","2",true)).status());assertEquals(403,request(null,"PUT","/api/skus/"+id,update("Changed","2",true)).status());assertEquals(401,request("missing-user","PUT","/api/skus/"+id,update("Changed","2",true)).status());
        for(String body:List.of(update("   ","2",true),update("a".repeat(121),"2",true),update("Changed","-1",true),update("Changed","1.001",true),update("Changed","1000000",true),"{\"name\":\"Changed\",\"price\":2}","{\"name\":\"Changed\",\"price\":null,\"active\":true}",update("Changed","2",true).replace("\"name\"","\"code\":\"OTHER\",\"name\"")))assertEquals(400,request("warehouse","PUT","/api/skus/"+id,body).status(),body);
        var unchanged=ok("warehouse","/api/skus/"+id);assertEquals("Original",unchanged.path("name").asText());assertEquals("SAFE",unchanged.path("code").asText());assertEquals(0,new BigDecimal("2.50").compareTo(unchanged.path("price").decimalValue()));
        for(String path:List.of("/api/skus/0","/api/skus/-1","/api/inventory/0"))assertEquals(400,request("warehouse","GET",path,null).status());
        assertEquals(404,request("warehouse","GET","/api/skus/9223372036854775807",null).status());assertEquals(404,request("warehouse","PUT","/api/skus/9223372036854775807",update("Missing","2",true)).status());assertEquals(404,request("warehouse","GET","/api/inventory/9223372036854775807",null).status());
    }
    @Test void singleStockAndExactSkuLedgerDoNotMixSimilarProductCodes()throws Exception{
        long first=sku("SIMILAR","First",10,3,true),second=sku("SIMILAR-2","Second",1,0,false);ledger(first);ledger(second);ledger(first);
        var stock=ok("warehouse","/api/inventory/"+first);assertEquals(first,stock.path("skuId").asLong());assertEquals("First",stock.path("name").asText());assertEquals(10,stock.path("onHand").asInt());assertEquals(3,stock.path("reserved").asInt());assertEquals(7,stock.path("available").asInt());assertEquals(0,new BigDecimal("2.50").compareTo(stock.path("price").decimalValue()));assertTrue(stock.path("active").asBoolean());
        var page=ok("warehouse","/api/inventory/ledger?skuId="+first+"&limit=1");assertEquals(1,page.path("items").size());assertEquals(first,page.path("items").get(0).path("skuId").asLong());assertFalse(page.path("nextCursor").isNull());
        var next=ok("admin","/api/inventory/ledger?skuId="+first+"&beforeId="+page.path("nextCursor").asLong());assertEquals(1,next.path("items").size());assertEquals(first,next.path("items").get(0).path("skuId").asLong());assertTrue(next.path("nextCursor").isNull());
        assertEquals(0,ok("warehouse","/api/inventory/ledger?skuId="+first+"&q=SIMILAR-2").path("items").size());assertEquals(3,ok("warehouse","/api/inventory/ledger?q=SIMILAR").path("items").size());
        for(String value:List.of("0","-1","oops","1.5"))assertEquals(400,request("warehouse","GET","/api/inventory/ledger?skuId="+value,null).status());
    }
}
