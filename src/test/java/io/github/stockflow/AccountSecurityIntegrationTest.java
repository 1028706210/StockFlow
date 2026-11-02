package io.github.stockflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"stockflow.auth.failure-limit=3","stockflow.auth.register-limit=100"})
@ActiveProfiles("test")
class AccountSecurityIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final JsonMapper json=JsonMapper.builder().build();
    static final String OLD="Synthetic-old-pass-39",NEW="Synthetic-new-pass-51";
    record Reply(int status,JsonNode body,HttpHeaders headers){}
    HttpClient browser(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    String name(){return "secure_"+UUID.randomUUID().toString().replace("-","").substring(0,16);}
    Reply call(HttpClient client,String method,String path,Object body,String token,String basic)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)builder.header("X-CSRF-TOKEN",token);
        if(basic!=null)builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        boolean form=body instanceof String;
        if(body!=null)builder.header("Content-Type",form?"application/x-www-form-urlencoded":"application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(form?(String)body:json.writeValueAsString(body)));
        var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),response.body().isBlank()?json.nullNode():json.readTree(response.body()),response.headers());
    }
    String token(HttpClient c)throws Exception{return call(c,"GET","/api/auth/csrf",null,null,null).body().path("token").asText();}
    Reply login(HttpClient c,String n,String password)throws Exception{return call(c,"POST","/api/auth/login","username="+n+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8),token(c),null);}
    long register(String n)throws Exception{var c=browser();var r=call(c,"POST","/api/auth/register",Map.of("username",n,"password",OLD),token(c),null);assertEquals(201,r.status(),r.body().toString());return r.body().path("id").asLong();}
    String admin(){return "admin:"+System.getenv("STOCKFLOW_DEMO_PASSWORD");}
    @BeforeEach void requireDedicatedSchema(){assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));}
    @Test void passwordChangeRequiresCurrentPasswordAndRevokesEveryOldSession()throws Exception{
        String n=name();long id=register(n);var a=browser();var b=browser();assertEquals(200,login(a,n,OLD).status());assertEquals(200,login(b,n,OLD).status());
        var wrong=call(a,"POST","/api/auth/password",Map.of("currentPassword","wrong-password","newPassword",NEW),token(a),null);assertEquals(400,wrong.status());
        assertEquals(403,call(a,"POST","/api/auth/password",Map.of("currentPassword",OLD,"newPassword",NEW),null,null).status());
        assertEquals(204,call(a,"POST","/api/auth/password",Map.of("currentPassword",OLD,"newPassword",NEW),token(a),null).status());
        var page=b.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/shop")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,page.statusCode(),"旧会话刷新公开页面仍应加载登录界面，业务API继续拒绝");assertTrue(page.body().contains("login-screen"));
        assertEquals(401,call(a,"GET","/api/auth/me",null,null,null).status());assertEquals(401,call(b,"GET","/api/auth/me",null,null,null).status());
        assertEquals(401,login(browser(),n,OLD).status());assertEquals(200,login(browser(),n,NEW).status());
        String audit=jdbc.queryForObject("SELECT after_json FROM operation_audit WHERE action='ACCOUNT_PASSWORD_CHANGE' AND entity_id=? ORDER BY id DESC LIMIT 1",String.class,id);
        assertFalse(audit.contains(OLD));assertFalse(audit.contains(NEW));assertFalse(audit.contains("passwordHash"));
    }
    @Test void disablingAndReenablingCannotReviveOldCookies()throws Exception{
        String n=name();long id=register(n);var user=browser();assertEquals(200,login(user,n,OLD).status());
        assertEquals(200,call(browser(),"PUT","/api/admin/accounts/"+id+"/enabled",Map.of("enabled",false),null,admin()).status());
        assertEquals(401,call(user,"GET","/api/skus",null,null,null).status());assertEquals(401,login(browser(),n,OLD).status());
        assertEquals(200,call(browser(),"PUT","/api/admin/accounts/"+id+"/enabled",Map.of("enabled",true),null,admin()).status());
        assertEquals(401,call(user,"GET","/api/auth/me",null,null,null).status());assertEquals(200,login(browser(),n,OLD).status());
    }
    @Test void adminAccessIsNarrowAndDoesNotExposeHashesOrAllowPrivilegeChanges()throws Exception{
        assertEquals(403,call(browser(),"GET","/api/admin/accounts",null,null,"warehouse:"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).status());
        var listing=call(browser(),"GET","/api/admin/accounts?q=buyer&page=1&size=8",null,null,admin());assertEquals(200,listing.status());
        assertFalse(listing.body().toString().contains("password"));assertTrue(listing.body().path("items").size()>0);
        assertEquals(409,call(browser(),"PUT","/api/admin/accounts/4/enabled",Map.of("enabled",false),null,admin()).status());
        assertEquals(400,call(browser(),"PUT","/api/admin/accounts/1/enabled",Map.of("enabled",true,"role","ADMIN"),null,admin()).status());
    }
    @Test void passwordValidationAndBasicFailureRateLimitRemainServerSide()throws Exception{
        String n=name();register(n);var c=browser();assertEquals(200,login(c,n,OLD).status());
        for(String invalid:List.of("short","界".repeat(25),OLD))assertEquals(400,call(c,"POST","/api/auth/password",Map.of("currentPassword",OLD,"newPassword",invalid),token(c),null).status());
        for(int i=0;i<3;i++)assertEquals(401,call(browser(),"GET","/api/auth/me",null,null,n+":wrong-password").status());
        var limited=login(browser(),n,OLD);assertEquals(429,limited.status());assertEquals("RATE_LIMITED",limited.body().path("code").asText());assertTrue(Integer.parseInt(limited.headers().firstValue("Retry-After").orElse("0"))>0);
        assertEquals(429,login(browser()," "+n+" ",OLD).status(),"账号名修剪不能绕过同一认证预算");
        for(String path:List.of("/api/auth/register","/api/auth/password"))assertEquals(429,call(browser(),"POST",path,Map.of(),null,n+":wrong-password").status(),"切换端点不能绕过 Basic 失败预算");
    }
    @Test void concurrentBasicFailuresConsumeTheSameAtomicBudget()throws Exception{
        String n=name();register(n);var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try{
            var tasks=new ArrayList<java.util.concurrent.Callable<Integer>>();for(int i=0;i<20;i++)tasks.add(()->call(browser(),"GET","/api/auth/me",null,null,n+":wrong-password").status());
            int rejected=0,limited=0;for(var result:pool.invokeAll(tasks)){int status=result.get();if(status==401)rejected++;else if(status==429)limited++;else fail("意外状态："+status);}
            assertEquals(3,rejected);assertEquals(17,limited);
        }finally{pool.shutdownNow();}
    }
}
