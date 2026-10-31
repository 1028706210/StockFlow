package io.github.stockflow.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts){this.accounts=accounts;}
    public record Identity(long id,String username,List<String> roles) {}
    public record Registration(String username,String password) {}
    public record Csrf(String token,String headerName) {}
    static Identity identity(Authentication auth){
        return new Identity(Actor.id(auth),auth.getName(),auth.getAuthorities().stream().map(a->a.getAuthority()).filter(role->role.startsWith("ROLE_")).toList());
    }
    @GetMapping("/api/auth/csrf")
    public Csrf csrf(CsrfToken token){return new Csrf(token.getToken(),token.getHeaderName());}
    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Identity register(@RequestBody Registration input){
        var account=accounts.register(input.username(),input.password());
        return new Identity(account.id(),account.getUsername(),account.getAuthorities().stream().map(a->a.getAuthority()).toList());
    }
    @GetMapping("/api/auth/me")
    public Identity me(Authentication auth){return identity(auth);}
    @GetMapping("/api/health")
    public Map<String,String> health(){return Map.of("status","UP","phase","P1");}
}
