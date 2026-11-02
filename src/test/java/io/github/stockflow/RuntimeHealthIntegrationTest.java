package io.github.stockflow;

import org.junit.jupiter.api.Test;
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
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RuntimeHealthIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final JsonMapper json=JsonMapper.builder().build();
    HttpResponse<String> get(String path,String user)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
        if(user!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8)));
        return client.send(b.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void publicHealthSeparatesProcessLivenessAndDatabaseReadiness()throws Exception{
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        var live=get("/api/health",null);assertEquals(200,live.statusCode());
        assertEquals("UP",json.readTree(live.body()).path("status").asText());
        var ready=get("/api/health/readiness",null);assertEquals(200,ready.statusCode(),ready.body());
        assertEquals("{\"status\":\"UP\"}",ready.body());
    }
    @Test void metricsAreBoundedAndOnlyVisibleToWarehouseAndAdmin()throws Exception{
        get("/api/health",null);
        assertEquals(401,get("/api/runtime/metrics",null).statusCode());
        assertEquals(403,get("/api/runtime/metrics","buyer").statusCode());
        var reply=get("/api/runtime/metrics","warehouse");assertEquals(200,reply.statusCode(),reply.body());
        var body=json.readTree(reply.body());
        assertTrue(body.path("requests").path("count").asLong()>0);
        assertTrue(body.path("requests").path("durationNanos").asLong()>0);
        assertEquals(5,body.path("requests").path("statusClasses").size());
        assertTrue(body.path("expiry").has("batches"));
        assertTrue(body.path("expiry").has("succeeded"));
        assertTrue(body.path("expiry").has("failed"));
        assertFalse(reply.body().contains("jdbc:"));assertFalse(reply.body().contains("password"));
        assertEquals(200,get("/api/runtime/metrics","admin").statusCode());
    }
    @Test void loginCanLoadAllNewModulesBeforeAuthentication()throws Exception{
        for(String path:java.util.List.of("/query-state.mjs","/business-documents.mjs","/document-state.mjs")){var response=get(path,null);assertEquals(200,response.statusCode(),path);assertTrue(response.headers().firstValue("content-type").orElse("").contains("javascript"),path);}
    }
    @Test void diagnosticStatusIsAdminOnlyAndContainsNoPrivateConfiguration()throws Exception{
        assertEquals(401,get("/api/admin/runtime",null).statusCode());
        assertEquals(403,get("/api/admin/runtime","buyer").statusCode());
        assertEquals(403,get("/api/admin/runtime","warehouse").statusCode());
        var reply=get("/api/admin/runtime","admin");assertEquals(200,reply.statusCode(),reply.body());
        var data=json.readTree(reply.body());assertEquals("UP",data.path("databaseStatus").asText());
        assertFalse(data.path("expiryEnabled").asBoolean());assertEquals(30000,data.path("expiryDelayMillis").asLong());
        assertFalse(data.path("observedAt").asText().isBlank());
        assertTrue(data.path("metrics").path("process").path("uptimeSeconds").asLong(-1)>=0);
        // 同一Spring测试上下文可能已经被履约测试手动扫描；禁用开关与实际批次历史是两个维度。
        assertTrue(java.util.Set.of("NEVER","RUNNING","SUCCEEDED","PARTIAL_FAILED","FAILED").contains(data.path("metrics").path("expiry").path("lastBatch").path("status").asText()));
        for(String forbidden:java.util.List.of("jdbc:","password","stackTrace","username","localhost","127.0.0.1"))assertFalse(reply.body().contains(forbidden),forbidden);
    }
    @Test void buyerStorefrontUsesPublicShellAndKeepsBusinessAuthentication()throws Exception{
        for(String path:java.util.List.of("/shop","/shop/")){
            var reply=get(path,null);assertEquals(200,reply.statusCode(),reply.body());
            assertTrue(reply.body().contains("storefront-header"));
        }
        for(String path:java.util.List.of("/assets/ui/storefront.mjs","/assets/ui/storefront.css"))assertEquals(200,get(path,null).statusCode(),path);
        assertEquals(401,get("/api/skus/page",null).statusCode());
        assertEquals(401,get("/api/orders",null).statusCode());
    }
}
