package io.github.stockflow.auth;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class AuthController {
    @GetMapping("/api/auth/me")
    public Map<String,Object> me(Authentication auth){
        return Map.of("id",Actor.id(auth),"username",auth.getName(),"roles",auth.getAuthorities().stream().map(a->a.getAuthority()).toList());
    }
    @GetMapping("/api/health")
    public Map<String,String> health(){return Map.of("status","UP","phase","P0");}
}
