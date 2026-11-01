package io.github.stockflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="stockflow.showcase.enabled=true")
@ActiveProfiles("test")
class ShowcaseGuardIntegrationTest {
    @LocalServerPort int port;
    int status(String user)throws Exception{
        String basic=Base64.getEncoder().encodeToString((user+":"+System.getenv("STOCKFLOW_DEMO_PASSWORD")).getBytes(StandardCharsets.UTF_8));
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/showcase/identity")).header("Authorization","Basic "+basic).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    @Test void enabledFlagDoesNotAllowWritingShowcaseDataIntoTheTestDatabase()throws Exception{assertEquals(409,status("warehouse"));}
    @Test void buyerCannotReadShowcaseEnvironmentIdentity()throws Exception{assertEquals(403,status("buyer"));}
}
