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
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventoryVisibilityIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json=JsonMapper.builder().build();
    record Reply(int status,JsonNode body) {}
    @BeforeEach void resetOnlyTestSchema(){
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        for(var table:List.of("operation_audit","inventory_ledger","return_item","customer_return","receipt_item","receipt_document","stocktake","order_item","customer_order","inventory","sku"))jdbc.update("DELETE FROM "+table);
    }
    Reply get(String user,String path)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/inventory/"+path)).timeout(Duration.ofSeconds(20));
        if(user!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8)));
        var r=client.send(b.GET().build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(),json.readTree(r.body()));
    }
    JsonNode ok(String path)throws Exception{var r=get("warehouse",path);assertEquals(200,r.status(),r.body().toString());return r.body();}
    long sku(String code,String name,int onHand,int reserved,boolean active){
        jdbc.update("INSERT INTO sku(code,name,price,active) VALUES(?,?,1,?)",code,name,active);
        long id=jdbc.queryForObject("SELECT id FROM sku WHERE code=?",Long.class,code);
        jdbc.update("INSERT INTO inventory(sku_id,on_hand,reserved) VALUES(?,?,?)",id,onHand,reserved);return id;
    }
    void ledger(long sku,String type,int quantity){jdbc.update("INSERT INTO inventory_ledger(sku_id,actor_id,operation_type,operation_key,quantity) VALUES(?,3,?,?,?)",sku,type,UUID.randomUUID().toString(),quantity);}
    @Test void onlyWarehouseAndAdminCanReadBothEndpoints()throws Exception{
        for(String endpoint:List.of("ledger","alerts")){
            assertEquals(200,get("warehouse",endpoint).status());assertEquals(200,get("admin",endpoint).status());
            assertEquals(403,get("buyer",endpoint).status());assertEquals(401,get(null,endpoint).status());
        }
    }
    @Test void ledgerPagesDescendingAndShowsPhysicalAndReservationDeltasWithoutKeys()throws Exception{
        long id=sku("EVENTS","Events",10,0,true);
        ledger(id,"INBOUND",10);ledger(id,"RESERVE",3);ledger(id,"RELEASE",2);ledger(id,"SHIP",1);
        var first=ok("ledger?limit=2");var rows=first.path("items");assertEquals(2,rows.size());
        assertEquals("SHIP",rows.get(0).path("operationType").asText());assertEquals(-1,rows.get(0).path("onHandDelta").asLong());assertEquals(-1,rows.get(0).path("reservedDelta").asLong());
        assertEquals("RELEASE",rows.get(1).path("operationType").asText());assertEquals(0,rows.get(1).path("onHandDelta").asLong());assertEquals(-2,rows.get(1).path("reservedDelta").asLong());
        assertFalse(rows.get(0).has("operationKey"));assertTrue(rows.get(0).path("orderId").isNull());assertFalse(rows.get(0).path("createdAt").asText().isBlank());
        long cursor=first.path("nextCursor").asLong();assertEquals(rows.get(1).path("id").asLong(),cursor);
        var second=ok("ledger?limit=2&beforeId="+cursor);assertTrue(second.path("nextCursor").isNull());
        assertEquals("RESERVE",second.path("items").get(0).path("operationType").asText());assertEquals(3,second.path("items").get(0).path("reservedDelta").asLong());
        assertEquals(0,second.path("items").get(0).path("onHandDelta").asLong());assertEquals(10,second.path("items").get(1).path("onHandDelta").asLong());assertEquals(0,second.path("items").get(1).path("reservedDelta").asLong());
        assertTrue(second.path("items").get(0).path("id").asLong()<cursor);
        var filtered=ok("ledger?type=RESERVE");assertEquals(1,filtered.path("items").size());assertEquals("RESERVE",filtered.path("items").get(0).path("operationType").asText());
    }
    @Test void searchTreatsPercentUnderscoreAndEscapeCharactersLiterally()throws Exception{
        long literal=sku("LITERAL_A","100% ! value",1,0,true),other=sku("LITERALXA","100 percent value",1,0,true);
        ledger(literal,"INBOUND",1);ledger(other,"INBOUND",1);
        for(String endpoint:List.of("ledger","alerts"))for(String query:List.of("%25","_","%21")){
            var rows=ok(endpoint+"?q="+query).path("items");assertEquals(1,rows.size());assertEquals(literal,rows.get(0).path("skuId").asLong());
        }
        assertEquals(0,ok("ledger?q=missing").path("items").size());assertEquals(0,ok("alerts?q=missing").path("total").asLong());
    }
    @Test void alertsCountCompleteSetBeyondNewestHundredAndExcludeThresholdEqualityAndInactive()throws Exception{
        long oldest=sku("LOW-OLD","Low stock",10,3,true);
        for(int n=0;n<105;n++)sku("HEALTHY-"+n,"Healthy",30,0,true);
        long newer=sku("LOW-NEW","Low stock",9,0,true);
        sku("BOUNDARY","Boundary",10,0,true);sku("INACTIVE","Low stock",0,0,false);
        var first=ok("alerts?limit=1");assertEquals(2,first.path("total").asLong());assertTrue(first.path("threshold").isNull());assertEquals(10,first.path("items").get(0).path("safetyStock").asInt());assertEquals(newer,first.path("items").get(0).path("skuId").asLong());
        var second=ok("alerts?limit=1&beforeId="+first.path("nextCursor").asLong());assertEquals(2,second.path("total").asLong());assertTrue(second.path("nextCursor").isNull());assertEquals(oldest,second.path("items").get(0).path("skuId").asLong());assertEquals(7,second.path("items").get(0).path("available").asLong());
        assertEquals(0,ok("alerts?threshold=7").path("total").asLong());assertEquals(1,ok("alerts?threshold=8").path("total").asLong());
        assertEquals(1,ok("alerts?q=LOW-OLD").path("total").asLong());
    }
    @Test void rejectsInvalidFiltersAndAcceptsEmptyResults()throws Exception{
        for(String endpoint:List.of("ledger","alerts")){
            for(String query:List.of("limit=0","limit=101","beforeId=0","beforeId=-1","beforeId=oops","q="+"a".repeat(121)))assertEquals(400,get("warehouse",endpoint+"?"+query).status(),query);
            var empty=ok(endpoint);assertEquals(0,empty.path("items").size());assertTrue(empty.path("nextCursor").isNull());
        }
        for(String query:List.of("threshold=0","threshold=1000001","threshold=oops"))assertEquals(400,get("warehouse","alerts?"+query).status());
        assertEquals(400,get("warehouse","ledger?type=UNKNOWN").status());
    }

    @Test void unicodeNameSearchWorksBesideAsciiSkuCodes()throws Exception{
        long id=sku("CUP-CN","海盐陶瓷杯",3,0,true);ledger(id,"INBOUND",3);
        for(String endpoint:List.of("ledger","alerts")){
            var match=ok(endpoint+"?q="+java.net.URLEncoder.encode("陶瓷",StandardCharsets.UTF_8));
            assertEquals(1,match.path("items").size());assertEquals(id,match.path("items").get(0).path("skuId").asLong());
            for(String query:List.of("不存在的商品","📦")){
                var none=ok(endpoint+"?q="+java.net.URLEncoder.encode(query,StandardCharsets.UTF_8));
                assertEquals(0,none.path("items").size());
            }
        }
    }
}
