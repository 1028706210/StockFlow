package io.github.stockflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthenticationIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    private static final String PASSWORD="Synthetic-test-pass-27";
    record Reply(int status,JsonNode body,HttpHeaders headers) {}
    record Token(String value,String header) {}
    HttpClient browser(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();}
    String username(){return "auth_"+UUID.randomUUID().toString().replace("-","").substring(0,18);}
    Reply request(HttpClient client,String method,String path,String body,String type,Token token,String basic) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        if(type!=null)b.header("Content-Type",type);
        if(token!=null)b.header(token.header(),token.value());
        if(basic!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        var response=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),response.body().isBlank()?json.nullNode():json.readTree(response.body()),response.headers());
    }
    Token csrf(HttpClient client) throws Exception {
        var r=request(client,"GET","/api/auth/csrf",null,null,null,null);
        assertEquals(200,r.status(),r.body().toString());
        assertFalse(r.body().path("token").asText().isBlank());
        assertFalse(r.body().path("headerName").asText().isBlank());
        return new Token(r.body().path("token").asText(),r.body().path("headerName").asText());
    }
    Reply register(HttpClient c,String name,String password,Token token) throws Exception {
        return request(c,"POST","/api/auth/register",json.writeValueAsString(Map.of("username",name,"password",password)),"application/json",token,null);
    }
    Reply login(HttpClient c,String name,String password,Token token) throws Exception {
        return request(c,"POST","/api/auth/login","username="+URLEncoder.encode(name,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8),"application/x-www-form-urlencoded",token,null);
    }
    Reply me(HttpClient c) throws Exception{return request(c,"GET","/api/auth/me",null,null,null,null);}

    @Test void registrationPersistsOnlyABuyerWithAHashAndDoesNotLogIn() throws Exception {
        var c=browser();String name=username();
        var registered=register(c,name,PASSWORD,csrf(c));
        assertEquals(201,registered.status(),registered.body().toString());
        long id=registered.body().path("id").asLong();assertTrue(id>=100);
        assertEquals(name,registered.body().path("username").asText());
        assertEquals(List.of("ROLE_BUYER"),json.convertValue(registered.body().path("roles"),List.class));
        assertFalse(registered.body().has("password"));assertFalse(registered.body().has("passwordHash"));
        assertEquals("stockflow_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        var row=jdbc.queryForMap("SELECT id,password_hash,role,enabled FROM app_user WHERE username=?",name);
        assertEquals(id,((Number)row.get("id")).longValue());assertEquals("BUYER",row.get("role"));
        String hash=(String)row.get("password_hash");assertNotEquals(PASSWORD,hash);assertTrue(new BCryptPasswordEncoder().matches(PASSWORD,hash));
        assertEquals(401,me(c).status());
        var another=browser();assertEquals(200,login(another,name,PASSWORD,csrf(another)).status());
        assertEquals(id,me(another).body().path("id").asLong());
        var denied=request(another,"POST","/api/skus","{\"code\":\"AUTH\",\"name\":\"Forbidden\",\"price\":1}","application/json",csrf(another),null);
        assertEquals(403,denied.status());
        assertEquals(id,request(browser(),"GET","/api/auth/me",null,null,null,name+":"+PASSWORD).body().path("id").asLong());
    }

    @Test void sessionLoginRotatesTheCookieAndLogoutRevokesTheSavedSession() throws Exception {
        var c=browser();var cookies=(CookieManager)c.cookieHandler().orElseThrow();var pre=csrf(c);
        String before=cookies.getCookieStore().getCookies().stream().filter(x->x.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        var logged=login(c,"buyer",System.getenv("STOCKFLOW_DEMO_PASSWORD"),pre);
        assertEquals(200,logged.status(),logged.body().toString());assertEquals(1,logged.body().path("id").asLong());
        assertEquals(List.of("ROLE_BUYER"),json.convertValue(logged.body().path("roles"),List.class));
        String after=cookies.getCookieStore().getCookies().stream().filter(x->x.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        assertNotEquals(before,after);
        String setCookie=String.join(";",logged.headers().allValues("Set-Cookie")).toLowerCase(Locale.ROOT);
        assertTrue(setCookie.contains("httponly"));assertTrue(setCookie.contains("samesite=lax"));
        var restored=me(c);assertEquals(200,restored.status());
        assertEquals(List.of("ROLE_BUYER"),json.convertValue(restored.body().path("roles"),List.class));
        assertEquals(403,request(c,"POST","/api/auth/logout",null,null,null,null).status());assertEquals(200,me(c).status());
        assertEquals(204,request(c,"POST","/api/auth/logout",null,null,csrf(c),null).status());
        assertEquals(401,me(c).status());
        var replay=HttpClient.newHttpClient();
        var response=replay.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/me")).header("Cookie","JSESSIONID="+after).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,response.statusCode());
    }

    @Test void anonymousAndCookieWritesRequireCsrfIncludingLoginAndRegister() throws Exception {
        var c=browser();String name=username();
        assertEquals(403,register(c,name,PASSWORD,null).status());
        assertEquals(403,login(c,"buyer",System.getenv("STOCKFLOW_DEMO_PASSWORD"),null).status());
        assertEquals(403,register(c,name,PASSWORD,new Token("invalid","X-CSRF-TOKEN")).status());
        assertEquals(200,login(c,"warehouse",System.getenv("STOCKFLOW_DEMO_PASSWORD"),csrf(c)).status());
        assertEquals(403,request(c,"POST","/api/skus","{\"code\":\"AUTH\",\"name\":\"Forbidden\",\"price\":1}","application/json",null,null).status());
        assertEquals(403,request(c,"POST","/api/auth/logout",null,null,new Token("invalid","X-CSRF-TOKEN"),null).status());
        var basic=request(browser(),"POST","/api/skus","{}","application/json",null,"warehouse:"+System.getenv("STOCKFLOW_DEMO_PASSWORD"));
        assertEquals(400,basic.status(),"Explicit Basic writes reach validation without a CSRF token");
    }

    @Test void invalidCredentialsAndUnknownOrExpiredSessionsReturnJson401() throws Exception {
        var c=browser();var token=csrf(c);
        var rejected=login(c,"buyer","invalid-password",token);
        assertEquals(401,rejected.status());assertEquals("UNAUTHENTICATED",rejected.body().path("code").asText());
        assertEquals(401,me(c).status());assertEquals(401,me(browser()).status());
        var manager=new CookieManager(null,CookiePolicy.ACCEPT_ALL);manager.getCookieStore().add(URI.create("http://127.0.0.1:"+port),new HttpCookie("JSESSIONID","expired-session"));
        assertEquals(401,me(HttpClient.newBuilder().cookieHandler(manager).build()).status());
    }

    @Test void registrationValidatesNamesPasswordsAndRejectsPrivilegeFields() throws Exception {
        var c=browser();var token=csrf(c);
        for(String name:List.of("ab","Uppercase","_invalid","a b","a".repeat(33)))assertEquals(400,register(c,name,PASSWORD,token).status(),name);
        for(String password:List.of("short","a".repeat(65),"界".repeat(25)))assertEquals(400,register(c,username(),password,token).status());
        for(String role:List.of("role","roles","id","enabled")){
            var r=request(c,"POST","/api/auth/register","{\"username\":\""+username()+"\",\"password\":\""+PASSWORD+"\",\""+role+"\":\"ADMIN\"}","application/json",token,null);
            assertEquals(400,r.status(),role);
        }
        assertEquals(201,register(c,username(),"界".repeat(24),token).status(),"72 UTF-8 bytes are valid");
        assertEquals(201,register(c,username(),"a".repeat(64),token).status(),"64 ASCII characters are valid");
    }

    @Test void duplicateAndConcurrentRegistrationsReturn409AndKeepTheOriginalPassword() throws Exception {
        var c=browser();var token=csrf(c);String name=username();
        assertEquals(201,register(c,name,PASSWORD,token).status());
        assertEquals(409,register(c,name,"another-valid-password",token).status());
        assertEquals(200,login(c,name,PASSWORD,token).status());
        var concurrentToken=csrf(c); // Initialize the rotated token once before racing the unique username.
        String raced=username();var pool=Executors.newFixedThreadPool(2);
        try{
            var results=pool.invokeAll(List.<Callable<Integer>>of(()->register(c,raced,PASSWORD,concurrentToken).status(),()->register(c,raced,PASSWORD,concurrentToken).status()));
            var statuses=new ArrayList<Integer>();for(var result:results)statuses.add(result.get());Collections.sort(statuses);
            assertEquals(List.of(201,409),statuses);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?",Integer.class,raced));
        }finally{pool.shutdownNow();}
    }
}
