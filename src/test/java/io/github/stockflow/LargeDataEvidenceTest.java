package io.github.stockflow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in synthetic query workload; never runs against the development schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="STOCKFLOW_LARGE_DATA_EVIDENCE",matches="true")
class LargeDataEvidenceTest {
    @Autowired JdbcTemplate jdbc;@LocalServerPort int port;
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    void clear(){assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));for(String table:List.of("operation_audit","inventory_ledger","return_item","customer_return","receipt_item","receipt_document","stocktake","order_item","customer_order","inventory","sku"))jdbc.update("DELETE FROM "+table);}
    @BeforeEach void setup(){clear();}
    @AfterEach void cleanup(){clear();}
    HttpResponse<String> get(String user,String path)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30)).header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8))).GET().build();return client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    @Test void measureRealFilteredQueriesAndExportBoundOnTenThousandOrders()throws Exception{
        long base=80000000L;var skus=new ArrayList<Object[]>();var inventory=new ArrayList<Object[]>();var orders=new ArrayList<Object[]>();var items=new ArrayList<Object[]>();var ledger=new ArrayList<Object[]>();var audit=new ArrayList<Object[]>();
        for(int n=0;n<1000;n++){skus.add(new Object[]{base+n,"LOAD-"+n,"测量商品 "+n});inventory.add(new Object[]{base+n});}
        for(int n=0;n<10000;n++){
            long sku=base+n%1000,order=base+n;orders.add(new Object[]{order,"large-"+n});items.add(new Object[]{order,sku});
            for(String type:List.of("INBOUND","RESERVE","SHIP"))ledger.add(new Object[]{sku,type.equals("INBOUND")?null:order,type,"large-"+n,type.equals("INBOUND")?3:1});
            audit.add(new Object[]{sku,"测量商品 "+n%1000,"large-data-"+n});
        }
        jdbc.batchUpdate("INSERT INTO sku(id,code,name,price) VALUES(?,?,?,2.50)",skus);jdbc.batchUpdate("INSERT INTO inventory(sku_id,on_hand,reserved) VALUES(?,20,0)",inventory);
        jdbc.batchUpdate("INSERT INTO customer_order(id,buyer_id,request_key,payload_hash,status,total,expires_at) VALUES(?,1,?,'fixture','COMPLETED',2.50,NOW())",orders);
        jdbc.batchUpdate("INSERT INTO order_item(order_id,sku_id,quantity,unit_price,sku_code,sku_name) SELECT ?,?,1,2.50,code,name FROM sku WHERE id=?",items.stream().map(row->new Object[]{row[0],row[1],row[1]}).toList());
        jdbc.batchUpdate("INSERT INTO inventory_ledger(sku_id,order_id,actor_id,operation_type,operation_key,quantity) VALUES(?,?,3,?,?,?)",ledger);
        jdbc.batchUpdate("INSERT INTO operation_audit(actor_id,action,entity_type,entity_id,entity_label,before_json,after_json,request_id) VALUES(3,'STOCK_POLICY','SKU',?,?,JSON_OBJECT('safetyStock',9),JSON_OBJECT('safetyStock',10),?)",audit);
        var scenarios=new LinkedHashMap<String,String>();scenarios.put("orderDeepPage","/api/orders/page?page=400&size=20&status=COMPLETED&minTotal=2.50&maxTotal=2.50");scenarios.put("ledger","/api/inventory/ledger?limit=20&type=SHIP");scenarios.put("audit","/api/audit?action=STOCK_POLICY&page=100&size=20");scenarios.put("reconciliation","/api/inventory/reconciliation?mismatchOnly=true");
        var samples=new LinkedHashMap<String,Object>();
        for(var entry:scenarios.entrySet()){
            String user=entry.getKey().startsWith("order")?"buyer":"warehouse";get(user,entry.getValue());var times=new ArrayList<Double>();
            for(int n=0;n<5;n++){long start=System.nanoTime();var response=get(user,entry.getValue());times.add((System.nanoTime()-start)/1e6);assertEquals(200,response.statusCode(),response.body());if(entry.getKey().equals("reconciliation"))assertEquals(0,json.readTree(response.body()).path("mismatchCount").asInt());if(entry.getKey().equals("orderDeepPage"))assertEquals(10000,json.readTree(response.body()).path("total").asInt());}
            Collections.sort(times);samples.put(entry.getKey(),Map.of("samplesMilliseconds",times,"medianMilliseconds",times.get(2),"maxMilliseconds",times.get(4)));
        }
        assertEquals(200,get("buyer","/api/orders/export?status=COMPLETED").statusCode());jdbc.update("INSERT INTO customer_order(buyer_id,request_key,payload_hash,status,total,expires_at) VALUES(1,'limit-extra','fixture','CANCELLED',0,NOW())");assertEquals(422,get("buyer","/api/orders/export").statusCode());assertEquals(200,get("buyer","/api/orders/export?q=SF-"+base).statusCode());
        var evidence=new LinkedHashMap<String,Object>();evidence.put("scope","Synthetic query load; 10000 completed orders,1000 SKUs,30000 ledger rows,10000 audit rows; five warm HTTP samples including Basic authentication; not a production capacity claim");evidence.put("environment",System.getProperty("os.name")+"; Java "+System.getProperty("java.version"));evidence.put("samples",samples);
        evidence.put("ledgerPlan",jdbc.queryForList("EXPLAIN SELECT id FROM inventory_ledger WHERE sku_id=? ORDER BY id DESC LIMIT 20",base));evidence.put("auditPlan",jdbc.queryForList("EXPLAIN SELECT id FROM operation_audit WHERE action='STOCK_POLICY' ORDER BY id DESC LIMIT 20 OFFSET 100"));evidence.put("orderPlan",jdbc.queryForList("EXPLAIN SELECT id FROM customer_order WHERE buyer_id=1 AND status='COMPLETED' AND total BETWEEN 2.50 AND 2.50 ORDER BY id DESC LIMIT 20 OFFSET 7980"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/large-data-evidence.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
}
