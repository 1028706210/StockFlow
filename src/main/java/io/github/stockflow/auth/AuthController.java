package io.github.stockflow.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts){this.accounts=accounts;}
    public record Identity(long id,String username,List<String> roles) {}
    public record Registration(String username,String password) {}
    public record Csrf(String token,String headerName) {}
    public record PasswordChange(@NotNull @Size(min=1,max=72)String currentPassword,@NotBlank @Size(max=64)String newPassword){}
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
    @PostMapping("/api/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(Authentication auth,@Valid @RequestBody PasswordChange input,HttpServletRequest request){
        accounts.changePassword(Actor.id(auth),input.currentPassword(),input.newPassword(),(String)request.getAttribute("requestId"));
        var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
    }
    @GetMapping("/api/health")
    public Map<String,String> health(){return Map.of("status","UP","phase","P1");}
}
