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
class RevisionIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json=JsonMapper.builder().build();
    record Reply(int status,JsonNode body) {}
    @BeforeEach void resetOnlyTestSchema(){
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        for(var table:List.of("operation_audit","inventory_ledger","return_item","customer_return","receipt_item","receipt_document","stocktake","order_item","customer_order","inventory","sku"))jdbc.update("DELETE FROM "+table);
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


    @Test void newOrderKeepsOriginalNameCodeAndPriceAfterCatalogChanges()throws Exception{
        long sku=sku("SNAPSHOT-CUP","原版随行杯",20,0,true);var placed=request("buyer","POST","/api/orders",purchase(UUID.randomUUID().toString(),sku));assertEquals(201,placed.status());long id=placed.body().path("id").asLong();
        jdbc.update("UPDATE sku SET name='第二代随行杯',price=99.00,active=FALSE WHERE id=?",sku);
        var item=ok("buyer","/api/orders/"+id).path("items").get(0);assertEquals("原版随行杯",item.path("skuName").asText());assertEquals("SNAPSHOT-CUP",item.path("skuCode").asText());assertEquals(0,new BigDecimal("2.50").compareTo(item.path("unitPrice").decimalValue()));
        assertEquals("原版随行杯",ok("warehouse","/api/fulfillment/orders/"+id).path("items").get(0).path("skuName").asText());
    }
    @Test void legacyOrderDoesNotInventHistoricalProductName()throws Exception{
        long sku=sku("LEGACY","当前名称",5,0,true),id=order(1,"COMPLETED","2.50");jdbc.update("INSERT INTO order_item(order_id,sku_id,quantity,unit_price) VALUES(?,?,1,2.50)",id,sku);
        var item=ok("buyer","/api/orders/"+id).path("items").get(0);assertTrue(item.path("skuName").isNull());assertTrue(item.path("skuCode").isNull());
    }
    @Test void orderFiltersApplyInclusiveDatesAmountsAndOwnership()throws Exception{
        long first=order(1,"COMPLETED","10.50"),second=order(1,"CANCELLED","20.00"),other=order(2,"COMPLETED","10.50");
        jdbc.update("UPDATE customer_order SET created_at='2026-10-01 00:00:00' WHERE id=?",first);jdbc.update("UPDATE customer_order SET created_at='2026-10-02 23:59:59.999999' WHERE id=?",second);jdbc.update("UPDATE customer_order SET created_at='2026-10-01 12:00:00' WHERE id=?",other);
        var r=ok("buyer","/api/orders/page?from=2026-10-01&to=2026-10-01&minTotal=10.50&maxTotal=10.50");assertEquals(1,r.path("total").asInt());assertEquals(first,r.path("items").get(0).path("id").asLong());
        assertEquals(1,ok("buyer","/api/orders/page?from=2026-10-02&to=2026-10-02").path("total").asInt());assertEquals(2,ok("warehouse","/api/fulfillment/orders/page?from=2026-10-01&to=2026-10-01").path("total").asInt());
        for(String query:List.of("from=2026-10-03&to=2026-10-01","minTotal=30&maxTotal=1","minTotal=-1","minTotal=1.001","from=invalid"))assertEquals(400,request("buyer","GET","/api/orders/page?"+query,null).status(),query);
    }
    @Test void filteredCsvExportsKeepRolesAndEscapeSpreadsheetCells()throws Exception{
        long own=order(1,"COMPLETED","12.25"),other=order(2,"COMPLETED","88.25");
        var response=csv("buyer","/api/orders/export?status=COMPLETED&maxTotal=20");assertEquals(200,response.statusCode(),response.body());assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/csv"));assertTrue(response.body().startsWith("\uFEFF"));assertTrue(response.body().contains("SF-"+own));assertFalse(response.body().contains("SF-"+other));
        assertEquals(403,csv("buyer","/api/fulfillment/orders/export").statusCode());assertEquals(403,csv("buyer","/api/inventory/export").statusCode());
        sku("CSV","=HYPERLINK(\"bad\",\"名称\")",5,0,true);String content=csv("warehouse","/api/inventory/export?q=CSV").body();assertTrue(content.contains("'=HYPERLINK"),content);assertTrue(content.contains("\"\"bad\"\""),content);
    }
    HttpResponse<String> csv(String user,String path)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8))).GET();return client.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    @Test void journalAuditAndStocktakesHonorDateBounds()throws Exception{
        long sku=sku("DATE-SKU","日期商品",5,0,true);ledger(sku);jdbc.update("UPDATE inventory_ledger SET created_at='2026-10-01 23:59:59.999999'");
        assertEquals(0,ok("warehouse","/api/inventory/ledger?from=2026-10-02").path("items").size());assertEquals(1,ok("warehouse","/api/inventory/ledger?to=2026-10-01").path("items").size());
        var count=request("warehouse","POST","/api/stocktakes","{\"requestKey\":\"date-test\",\"skuId\":"+sku+",\"targetOnHand\":5,\"reason\":\"日期测试\"}");assertEquals(201,count.status());jdbc.update("UPDATE stocktake SET created_at='2026-10-01 00:00:00'");jdbc.update("UPDATE operation_audit SET created_at='2026-10-01 00:00:00'");
        assertEquals(0,ok("warehouse","/api/stocktakes?from=2026-10-02").path("total").asInt());assertEquals(0,ok("warehouse","/api/audit?from=2026-10-02").path("total").asInt());
        for(String path:List.of("/api/stocktakes","/api/audit","/api/inventory/ledger"))assertEquals(400,request("warehouse","GET",path+"?from=2026-10-03&to=2026-10-01",null).status());
    }
}
